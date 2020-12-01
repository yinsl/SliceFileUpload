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

	@Value("${tempFilePath}")
	private String tempFilePath;

	@Value("tempFileAlivesMillisecond")
	private long tempFileAlivesMillisecond;

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

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
			String fileId = files[i].getName().substring(0, 32);
			String partCount = stringRedisTemplate.opsForValue().get("partCount" + "_" + fileId);
			String uploadedSlicesCount = stringRedisTemplate.opsForValue().get("uploadedSlicesCount" + "_" + fileId);
			// 分片总数和已上传分片数相同，说明上传结束，可以合并文件
			if (partCount != null && partCount.equals(uploadedSlicesCount)) {
				cacheMap.put(fileId, Integer.valueOf(partCount));
			}
		}

		//对可以合并的临时文件执行合并操作
		for (String fileId : cacheMap.keySet()) {
			mergeFile(fileId, cacheMap.get(fileId));
		}

		//对超过存储时间的临时文件执行清理操作
		files = file.listFiles();
		for (int i = 0; i < files.length; i++) {
			File temp = files[i];
			if (now - temp.lastModified() > tempFileAlivesMillisecond) {
				temp.delete();
			}
		}
	}

	/**
	 * 合并文件，删除临时文件和相关缓存信息
	 * 
	 * @param fileId
	 * @param partCount
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	private void mergeFile(String fileId, int partCount) throws FileNotFoundException, IOException {
		String fileName = stringRedisTemplate.opsForValue().get("fileName" + "_" + fileId);
		if (fileName == null) {
			return;
		}
		try (FileOutputStream fosTemp = new FileOutputStream(tempFilePath + fileName);) {
			for (int i = 0; i < partCount; i++) {
				String tempFileName = fileId + "_" + i + ".temp";
				String path = tempFilePath + tempFileName;
				try (FileInputStream fisTemp = new FileInputStream(path);) {
					int buffSize = 1024;
					byte[] buff = new byte[buffSize];
					int len;
					while ((len = fisTemp.read(buff)) != -1) {
						fosTemp.write(buff, 0, len);
					}
				}
			}
		}
		// 文件合并完毕，删除临时文件和缓存
		for (int i = 0; i < partCount; i++) {
			String tempFileName = fileId + "_" + i + ".temp";
			String path = tempFilePath + tempFileName;
			File temp = new File(path);
			if (temp.exists()) {
				temp.delete();
				stringRedisTemplate.opsForHash().delete(fileId, i);
			}
		}
		stringRedisTemplate.delete("fileName" + "_" + fileId);
		stringRedisTemplate.delete("partCount" + "_" + fileId);
		stringRedisTemplate.delete("uploadedSlicesCount" + "_" + fileId);
	}

}
