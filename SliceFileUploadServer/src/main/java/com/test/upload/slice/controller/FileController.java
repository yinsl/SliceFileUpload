package com.test.upload.slice.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.test.upload.slice.utils.MD5;

@RestController
public class FileController {

	@Value("${filePath}")
	private String filePath;

	@Value("${tempFilePath}")
	private String tempFilePath;

	@Autowired
	private RedisTemplate<String, Serializable> redisTemplate;

	/**
	 * 处理文件上传
	 * 
	 * @param fileId
	 * @param partCount
	 * @param partSize
	 * @param sliceIndex
	 * @param req
	 * @return
	 * @throws InterruptedException
	 */
	@RequestMapping("/upload/{fileId}/{partCount}/{partSize}/{sliceIndex}")
	public @ResponseBody String upload(@PathVariable("fileId") String fileId, @PathVariable("partCount") Long partCount,
			@PathVariable("partSize") Long partSize, @PathVariable("sliceIndex") Integer sliceIndex,
			HttpServletRequest req) throws InterruptedException {
		int buffSize = 1024;
		byte[] buff = new byte[buffSize];
		String tempFileName = fileId + "_" + sliceIndex + ".temp";
		File file = new File(tempFilePath + tempFileName);
		if (file.exists()) {
			// 删除不完整的分片文件
			file.delete();
		}
		try (InputStream is = req.getInputStream();
				FileOutputStream fos = new FileOutputStream(tempFilePath + tempFileName);) {

			// 已经读了多少
			int hasReaded = 0;
			boolean key = true;
			while (key) {
				// 本次读到的长度
				int len = is.read(buff, 0, buffSize);
				if (len == -1) {
					break;
				}
				System.out.println("========len======" + len);
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
			System.out.println(tempFileName + "上传失败!");
			return "uploading failure";
		}
		// 更新已上传文件分片信息
		updateUploadedSlices(fileId, sliceIndex, 2 * 24 * 60 * 60);
		System.out.println(tempFileName + "上传成功!");
		return "uploading success";
	}

	/**
	 * 更新已上传文件分片信息
	 * 
	 * @param fileId
	 * @param sliceIndex
	 * @throws InterruptedException
	 */
	private void updateUploadedSlices(String key, int sliceIndex, int timeoutSeconds) throws InterruptedException {
		redisTemplate.opsForHash().put(key, sliceIndex, sliceIndex);
		// 两天后过期
		redisTemplate.expire(key, timeoutSeconds, TimeUnit.SECONDS);
		System.out.println("redis=== key:: " + key + ", field: " + sliceIndex + ", value: "
				+ redisTemplate.opsForHash().get(key, sliceIndex));
	}

	/**
	 * 查询已经上传的分片
	 * 
	 * @param fileId 文件ID
	 * @return 分片格式：“,1,3,4,7,”，即分片前后都用逗号分隔，其中的分片为已经上传的分片索引。
	 */
	@RequestMapping("/uploaded/{fileId}")
	public String uploadedSlices(@PathVariable("fileId") String fileId) {
		Set<Object> set = redisTemplate.opsForHash().keys(fileId);
		String result = null;
		if (set != null && set.size() > 0) {
			result = ",";
			for (Object o : set) {
				result += o.toString() + ",";
			}
		}
		System.out.println(result);
		return result;
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
			String md5 = MD5.getFileMD5String(new File(filePath + fileName));
			System.out.println("merge file md5 " + md5);
			System.out.println("origin file md5 " + fileId);
		} catch (Exception e) {
			e.printStackTrace();
			return "failure";
		}
		// 文件合并完毕，删除临时文件和缓存
		for (int i = 0; i < partCount; i++) {
			String tempFileName = fileId + "_" + i + ".temp";
			String path = tempFilePath + tempFileName;
			File temp = new File(path);
			if (temp.exists()) {
				temp.delete();
				redisTemplate.opsForHash().delete(fileId, i);
			}
		}

		return "success";
	}
}
