package com.test.upload.slice.controller;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FileController {

	@Value("${filePath}")
	private String filePath;

	@Value("${tempFilePath}")
	private String tempFilePath;

	// 处理文件上传
	@RequestMapping("/upload/{partSize}/{tempFileName}")
	public @ResponseBody String upload(@PathVariable("partSize") Long partSize,
			@PathVariable("tempFileName") String tempFileName, HttpServletRequest req) {
		int buffSize = 1024;
		byte[] buff = new byte[buffSize];
		try (InputStream is = req.getInputStream();
				FileOutputStream fos = new FileOutputStream(tempFilePath + tempFileName);) {
			
			// 已经读了多少
			int hasReaded = 0;
			boolean key = true;
			while (key) {
				// 本次读到的长度
				int len = is.read(buff, 0, buffSize);
				// temp用于临界判断
				int temp = hasReaded + len;
				if (temp >= partSize) {
					key = false;
					len = (int) (partSize - hasReaded);
				}
				fos.write(buff, 0, len);
				hasReaded = temp;
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(tempFileName+"上传失败!");
			return "uploading failure";
		}
		System.out.println(tempFileName+"上传成功!");
		return "uploading success";
	}

	/**
	 * 文件合并
	 * 
	 * @return 文件合并结果：sucess | failure
	 */
	@RequestMapping("/merge/{fileId}/{partCount}/{fileName}")
	public String mergePartFile(@PathVariable("fileId") String fileId, @PathVariable("partCount") Long partCount,
			@PathVariable("fileName") String fileName, HttpServletRequest req) {
		try (FileOutputStream fosTemp = new FileOutputStream(filePath + fileName);) {
			for (int i = 0; i < partCount; i++) {
				String tempFileName = "temp" + "_" + fileId + "_" + i + ".temp";
				String filePath = tempFilePath + tempFileName;
				try (FileInputStream fisTemp = new FileInputStream(filePath);) {
					int buffSize = 1024;
					byte[] buff = new byte[buffSize];
					int len;
					while ((len = fisTemp.read(buff)) != -1) {
						fosTemp.write(buff, 0, len);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return "failure";
		}
		return "success";
	}
}
