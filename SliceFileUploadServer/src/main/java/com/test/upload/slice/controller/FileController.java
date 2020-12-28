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

import com.test.upload.slice.constant.UploadStatus;
import com.test.upload.slice.dto.SliceUploadFileInfo;
import com.test.upload.slice.utils.AESUtil;
import com.test.upload.slice.utils.MD5;

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

	@Value("${intervalTime}")
	private int intervalTime;

	@Autowired
	private RedisTemplate<String, ?> redisTemplate;

	/**
	 * 获取文件上传鉴权参数
	 * 
	 * @param fileName
	 * @param fileMD5
	 * @param fileSize
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/uploadAuth/{vin}/{fileMD5}/{fileSize}/{fileName}")
	public @ResponseBody SliceUploadFileInfo uploadFileInfo(@PathVariable("fileName") String fileName,
			@PathVariable("fileMD5") String fileMD5, @PathVariable("vin") String vin,
			@PathVariable("fileSize") long fileSize) throws Exception {

		byte[] aesKey = AESUtil.generateDesKey128();
		String token = UUID.randomUUID().toString().replace("-", "");
		String eventId = UUID.randomUUID().toString().replace("-", "");
		String base64AesKey = Base64.encodeBase64String(aesKey);

		// 分片总数
		int totalSlices = (int) (fileSize / sliceSize);
		if (sliceSize * totalSlices < fileSize) {
			totalSlices++;
		}

		int[] unUploadedSliceIndexes = new int[totalSlices];

		for (int i = 0; i < totalSlices; i++) {
			unUploadedSliceIndexes[i] = i;
		}

		redisTemplate.opsForHash().put(eventId, "aesKey", base64AesKey);
		redisTemplate.opsForHash().put(eventId, "token", token);
		redisTemplate.opsForHash().put(eventId, "fileSize", fileSize);
		redisTemplate.opsForHash().put(eventId, "fileMD5", fileMD5);
		redisTemplate.opsForHash().put(eventId, "fileName", fileName);
		redisTemplate.opsForHash().put(eventId, "sliceSize", sliceSize);
		redisTemplate.opsForHash().put(eventId, "totalSlices", totalSlices);
		redisTemplate.opsForHash().put(eventId, "intervalTime", intervalTime);
		redisTemplate.opsForHash().put(eventId, "vin", vin);
		//值为0表示上传中，值为1表示上传成功，值为2
		redisTemplate.opsForHash().put(eventId, "uploadStatus", UploadStatus.UPLOADING);

		redisTemplate.expire(eventId, tempFileCacheMillisecond, TimeUnit.SECONDS);

		return new SliceUploadFileInfo(eventId, base64AesKey, token, sliceSize, unUploadedSliceIndexes,intervalTime);
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
		redisTemplate.opsForHash().put(eventId + "_uploadedSliceIndexes", sliceIndex.toString(), sliceIndex.toString());
		redisTemplate.expire(eventId + "_uploadedSliceIndexes", tempFileCacheMillisecond, TimeUnit.SECONDS);

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
	 * 查询未上传文件分片信息
	 * 
	 * @param eventId 文件ID
	 */
	@RequestMapping("/unUploaded/{eventId}")
	public @ResponseBody SliceUploadFileInfo unUploadedSlices(@PathVariable("eventId") String eventId) {
		SliceUploadFileInfo sufi = null;
		Set<Object> set = redisTemplate.opsForHash().keys(eventId + "_uploadedSliceIndexes");

		if (set != null && set.size() > 0) {
			Integer totalSlices = (Integer) redisTemplate.opsForHash().get(eventId, "totalSlices");

			int[] sliceIndexes = new int[totalSlices];
			for (Object o : set) {
				// 已经上传的设为1
				sliceIndexes[Integer.parseInt(o.toString())] = 1;
			}

			int[] unUploadedIndexes = new int[totalSlices - set.size()];
			for (int i = 0, j = 0; i < totalSlices; i++) {
				if (sliceIndexes[i] == 0) {
					unUploadedIndexes[j] = i;
					j++;
				}
			}

			String aesKey = redisTemplate.opsForHash().get(eventId, "aesKey").toString();
			String token = redisTemplate.opsForHash().get(eventId, "token").toString();
			Integer sliceSize = Integer.parseInt(redisTemplate.opsForHash().get(eventId, "sliceSize").toString());
			int intervalTime = Integer.parseInt(redisTemplate.opsForHash().get(eventId, "intervalTime").toString());
			sufi = new SliceUploadFileInfo(eventId, aesKey, token, sliceSize, unUploadedIndexes, intervalTime);
		}
		return sufi;
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
			//合并完成进行MD5校验
			File mergedFile = new File(filePath + fileName);
			String currentMD5 = MD5.getFileMD5String(mergedFile);
			String cacheMD5 = redisTemplate.opsForHash().get(eventId, "fileMD5").toString();
			//MD5校验失败，删除文件并报错
			if (!cacheMD5.equals(currentMD5)) {
				mergedFile.delete();
				return "failure";
			}
		} catch (Exception e) {
			e.printStackTrace();
			return "failure";
		}
		// 文件合并完毕，删除临时文件
		cleanSlices(eventId);
		redisTemplate.opsForHash().put(eventId, "uploadStatus", UploadStatus.SUCCESS);
		return "success";
	}

	@RequestMapping("/cleanSlices/{eventId}")
	public String cleanSlices(String eventId) {
		Integer totalSlices = Integer.parseInt(redisTemplate.opsForHash().get(eventId, "totalSlices").toString());
		// 删除临时文件
		for (int i = 0; i < totalSlices; i++) {
			String tempFileName = eventId + "_" + i + ".temp";
			String path = tempFilePath + tempFileName;
			File temp = new File(path);
			if (temp.exists()) {
				temp.delete();
			}
		}
		redisTemplate.opsForHash().put(eventId, "uploadStatus", UploadStatus.FAIL);
		return "success";
	}
	
	@RequestMapping("/uploadStatus/{eventId}")
	public String uploadStatus(String eventId) {
		UploadStatus uploadStatus = (UploadStatus)redisTemplate.opsForHash().get(eventId, "uploadStatus");
		return uploadStatus == null ? UploadStatus.NOTFOUND.getName() : uploadStatus.getName();
	}
	
}
