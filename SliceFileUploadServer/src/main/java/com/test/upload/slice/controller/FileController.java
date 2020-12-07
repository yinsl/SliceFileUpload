package com.test.upload.slice.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.test.upload.slice.dto.UploadAuthInfo;
import com.test.upload.slice.utils.AESUtil;

/**
 * 文件分片上传
 *
 */
@RestController
public class FileController {

	@Value("${filePath}")
	private String filePath;

	@Value("${tempFilePath}")
	private String tempFilePath;

	@Value("${tempFileCacheMillisecond}")
	private long tempFileCacheMillisecond;

	@Value("${sliceSize}")
	private int sliceSize;

	@Autowired
	private RedisTemplate<String, ?> redisTemplate;

	@RequestMapping("/uploadAuth/{fileMD5}/{fileSize}/{fileName}")
	public @ResponseBody UploadAuthInfo uploadFileInfo(@PathVariable("fileName") String fileName,
			@PathVariable("fileMD5") String fileMD5, @PathVariable("fileSize") long fileSize, HttpServletRequest req)
			throws Exception {
		byte[] aesKey = AESUtil.generateDesKey128();
		String token = UUID.randomUUID().toString().replace("-", "");
		String eventId = UUID.randomUUID().toString().replace("-", "");
		String base64AesKey = Base64.encodeBase64String(aesKey);
		redisTemplate.opsForHash().put(eventId, "aesKey", base64AesKey);
		redisTemplate.opsForHash().put(eventId, "token", token);
		redisTemplate.opsForHash().put(eventId, "fileSize", fileSize);
		redisTemplate.opsForHash().put(eventId, "fileMD5", fileMD5);
		redisTemplate.opsForHash().put(eventId, "fileName", fileName);
		redisTemplate.opsForHash().put(eventId, "sliceSize", sliceSize);

		long totalSlices = 0;

		// 分多少段
		totalSlices = (int) (fileSize / sliceSize);
		if (sliceSize * totalSlices < fileSize) {
			totalSlices++;
		}

		// 缓存分片总数
		redisTemplate.opsForHash().put(eventId, "totalSlices", totalSlices);

		redisTemplate.expire(eventId, tempFileCacheMillisecond, TimeUnit.SECONDS);

		return new UploadAuthInfo(eventId, base64AesKey, token, sliceSize);
	}

	/**
	 * 处理分片文件上传
	 */
	@RequestMapping("/upload/{sliceIndex}/{encryptedSliceSize}")
	public @ResponseBody String upload(@PathVariable("sliceIndex") Integer sliceIndex,
			@PathVariable("encryptedSliceSize") int encryptedSliceSize, HttpServletRequest req)
			throws InterruptedException {

		String eventId = req.getHeader("eventId");

		Object cacheToken = redisTemplate.opsForHash().get(eventId, "token");
		if (cacheToken == null) {
			return "uploading failure, token invalided.";
		}
		byte[] aesKey = Base64.decodeBase64(redisTemplate.opsForHash().get(eventId, "aesKey").toString());

		ByteBuffer bb = ByteBuffer.allocate(encryptedSliceSize);

		int buffSize = 1024;
		byte[] buff = new byte[buffSize];
		String tempFileName = eventId + "_" + sliceIndex + ".temp";
		File file = new File(tempFilePath + tempFileName);
		if (file.exists()) {
			// 删除不完整的分片文件
			file.delete();
		}
		// temp用于临界判断
		int temp = 0;
		// 已经读了多少
		int hasReaded = 0;
		try (InputStream is = req.getInputStream();
				FileOutputStream fos = new FileOutputStream(tempFilePath + tempFileName);) {

			boolean key = true;
			while (key) {
				// 本次读到的长度
				int len = is.read(buff, 0, buffSize);
				if (len == -1) {
					break;
				}
				temp = hasReaded + len;
				if (temp >= encryptedSliceSize) {
					key = false;
					len = (int) (encryptedSliceSize - hasReaded);
				}
				bb.put(buff, 0, len);
				hasReaded = temp;
			}
			bb.flip();
			byte[] slice = new byte[bb.limit()];
			bb.get(slice);
			// 对接收的数据进行AES解密
			AESUtil aesUtil = new AESUtil();
			byte[] result = aesUtil.decrypt(slice, aesKey);
			// 明文写入临时文件
			fos.write(result);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(tempFileName + "上传失败!");
			return "uploading failure";
		}

		// 缓存已经上传的分片数
		redisTemplate.opsForHash().increment(eventId, "totalUploadedSlices", 1);

		// 缓存已上传分片的索引信息
		redisTemplate.opsForHash().put(eventId + "_sliceIndex", sliceIndex.toString(), sliceIndex.toString());
		redisTemplate.expire(eventId + "_sliceIndex", tempFileCacheMillisecond, TimeUnit.SECONDS);

		int totalSlices = Integer.parseInt(redisTemplate.opsForHash().get(eventId, "totalSlices").toString());
		int totalUploadedSlices = Integer
				.parseInt(redisTemplate.opsForHash().get(eventId, "totalUploadedSlices").toString());
		if (totalSlices == totalUploadedSlices) {
			mergeSliceFile(eventId);
		}
		System.out.println(tempFileName + "上传成功!");
		return "uploading success";
	}

	/**
	 * 查询已经上传的分片
	 * 
	 * @param eventId 文件ID
	 * @return 分片格式：“,1,3,4,7,”，即分片前后都用逗号分隔，其中的分片为已经上传的分片索引。
	 */
	@RequestMapping("/uploaded/{eventId}")
	public String uploadedSlices(@PathVariable("eventId") String eventId) {
		String result = null;
		Set<Object> set = redisTemplate.opsForHash().keys(eventId + "_sliceIndex");
		if (set != null && set.size() > 0) {
			result = ",";
			for (Object o : set) {
				result += o.toString() + ",";
			}
		}
		return result;
	}

	/**
	 * 文件合并
	 * 
	 * @return 文件合并结果：sucess | failure
	 */
	private String mergeSliceFile(String eventId) {
		String fileName = redisTemplate.opsForHash().get(eventId, "fileName").toString();
		Integer totalSlices = Integer.parseInt(redisTemplate.opsForHash().get(eventId, "totalSlices").toString());
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
		} catch (Exception e) {
			e.printStackTrace();
			return "failure";
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
		return "success";
	}
}
