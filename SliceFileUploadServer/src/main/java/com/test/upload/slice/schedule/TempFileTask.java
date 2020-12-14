package com.test.upload.slice.schedule;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TempFileTask {

	@Value("${filePath}")
	private String filePath;

	@Value("${tempFilePath}")
	private String tempFilePath;

	@Value("tempFileAlivesMillisecond")
	private String tempFileAlivesMillisecond;

	@Autowired
	private StringRedisTemplate redisTemplate;

	/**
	 * 每天0点执行一次，合并所有分片已经正确上传的文件并删除最后更新日期在5天前的临时文件
	 * 
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	@Scheduled(cron = "0 0 0 * * ?")
	public void cleanTempFiles() throws FileNotFoundException, IOException {
		File dir = new File(tempFilePath);
		File[] files = dir.listFiles();

		Set<String> mergeSet = new HashSet<>();
		Set<String> cleanSet = new HashSet<>();

		for (int i = 0; i < files.length; i++) {
			String eventId = files[i].getName().substring(0, 32);
			boolean cacheExist = redisTemplate.hasKey(eventId);
			if (!cacheExist) {
				//缓存过期，对应的文件为垃圾，需要清理
				cleanSet.add(eventId);
			} else {
				Object totalSlices = redisTemplate.opsForHash().get(eventId, "totalSlices");
				Object totalUploadedSlices = redisTemplate.opsForHash().get(eventId, "totalUploadedSlices");
				// 分片总数和已上传分片数相同，说明上传结束，可以合并文件
				if (totalSlices != null && totalUploadedSlices != null
						&& totalSlices.toString().equals(totalUploadedSlices.toString())) {
					mergeSet.add(eventId);
				}
			}
		}

		// 对可以合并的临时文件执行合并操作
		for (String eventId : mergeSet) {
			mergeFile(eventId);
		}

		// 对超过存储时间的临时文件执行清理操作
		for (String eventId: cleanSet) {
			int i = 0;
			while (true) {
				String tempFileName = eventId + "_" + i + ".temp";
				String path = tempFilePath + tempFileName;
				File file = new File(path);
				if (file.exists()) {
					i++;
					file.delete();
				} else {
					break;
				}
			}
		}
	}

	/**
	 * 合并文件，删除临时文件和相关缓存信息
	 * 
	 * @param eventId
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	private void mergeFile(String eventId) throws FileNotFoundException, IOException {
		Object fileName = redisTemplate.opsForHash().get(eventId, "fileName");
		if (fileName == null) {
			return;
		}
		int totalSlices = Integer.parseInt(redisTemplate.opsForHash().get(eventId, "totalSlices").toString());
		try (FileOutputStream fos = new FileOutputStream(filePath + fileName);) {
			for (int i = 0; i < totalSlices; i++) {
				String tempFileName = eventId + "_" + i + ".temp";
				String path = tempFilePath + tempFileName;
				try (FileInputStream fisTemp = new FileInputStream(path);) {
					int buffSize = 1024;
					byte[] buff = new byte[buffSize];
					int len;
					while ((len = fisTemp.read(buff)) != -1) {
						fos.write(buff, 0, len);
					}
				}
			}
		}
		// 文件合并完毕，删除临时文件
		for (int i = 0; i < totalSlices; i++) {
			String tempFileName = eventId + "_" + i + ".temp";
			String path = tempFilePath + tempFileName;
			File temp = new File(path);
			if (temp.exists()) {
				temp.delete();
			}
		}

		// 删除缓存
		redisTemplate.delete(eventId);
	}

}
