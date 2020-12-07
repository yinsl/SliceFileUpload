package com.test.upload.slice.schedule;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	@Scheduled(cron = "0 0 0 * * ?")
	public void cleanTempFiles() throws FileNotFoundException, IOException {
		long now = System.currentTimeMillis();
		File file = new File(tempFilePath);
		File[] files = file.listFiles();

		Map<String, Integer> cacheMap = new HashMap<>();

		// 查找可以合并的临时文件列表
		for (int i = 0; i < files.length; i++) {
			String eventId = files[i].getName().substring(0, 32);
			Object totalSlices = redisTemplate.opsForHash().get(eventId, "totalSlices");
			Object totalUploadedSlices = redisTemplate.opsForHash().get(eventId, "totalUploadedSlices");
			// 分片总数和已上传分片数相同，说明上传结束，可以合并文件
			if (totalSlices != null && totalUploadedSlices != null && totalSlices.toString().equals(totalUploadedSlices.toString())) {
				cacheMap.put(eventId, Integer.parseInt(totalSlices.toString()));
			}
		}

		//对可以合并的临时文件执行合并操作
		for (String eventId : cacheMap.keySet()) {
			mergeFile(eventId, cacheMap.get(eventId));
		}

		//对超过存储时间的临时文件执行清理操作
		files = file.listFiles();
		for (int i = 0; i < files.length; i++) {
			File temp = files[i];
			if (now - temp.lastModified() > Long.valueOf(tempFileAlivesMillisecond)) {
				temp.delete();
			}
		}
	}

	/**
	 * 合并文件，删除临时文件和相关缓存信息
	 * 
	 * @param eventId
	 * @param totalSlices
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	private void mergeFile(String eventId, int totalSlices) throws FileNotFoundException, IOException {
		Object fileName = redisTemplate.opsForHash().get(eventId, "fileName");
		if (fileName == null) {
			return;
		}
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
		
		//删除缓存
		redisTemplate.delete(eventId);
	}

}
