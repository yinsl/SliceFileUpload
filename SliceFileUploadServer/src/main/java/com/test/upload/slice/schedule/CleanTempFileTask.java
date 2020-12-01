package com.test.upload.slice.schedule;

import java.io.File;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CleanTempFileTask {

	/**
	 * 每天0点执行一次，删除最后更新日期在5天前的临时文件
	 */
	@Scheduled(cron = "0 0 0 * * ?")
    public void execute() {
		long now = System.currentTimeMillis();
		long fiveDays = 5 * 24 * 60 * 60 * 1000;
		String path = "d:/test/temp";
		File file = new File(path);
		File[] files = file.listFiles();
		for (int i = 0; i < files.length; i++) {
			File temp = files[i];
			if (now - temp.lastModified() > fiveDays) {
				temp.delete();
			}
		}
    }
	
}
