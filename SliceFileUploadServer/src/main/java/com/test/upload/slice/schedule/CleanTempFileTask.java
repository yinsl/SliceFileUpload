package com.test.upload.slice.schedule;

import java.io.File;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CleanTempFileTask {
	
	@Value("${tempFilePath}")
	private String tempFilePath;
	
	@Value("tempFileAlivesMillisecond")
	private long tempFileAlivesMillisecond;

	/**
	 * 每天0点执行一次，删除最后更新日期在5天前的临时文件
	 */
	@Scheduled(cron = "0 0 0 * * ?")
    public void execute() {
		long now = System.currentTimeMillis();
		File file = new File(tempFilePath);
		File[] files = file.listFiles();
		for (int i = 0; i < files.length; i++) {
			File temp = files[i];
			if (now - temp.lastModified() > tempFileAlivesMillisecond) {
				temp.delete();
			}
		}
    }
	
}
