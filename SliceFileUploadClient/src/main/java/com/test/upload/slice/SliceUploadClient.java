package com.test.upload.slice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import com.test.upload.slice.utils.MD5;

/**
 * 大文件分片上传客户端
 *
 */
public class SliceUploadClient {

	/**
	 * 上传分片文件的线程池
	 */
	private static ExecutorService SERVICE = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

	/**
	 * 分片文件上传的链接地址
	 */
	private static String uploadUrl = "http://localhost:8182/upload/";

	/**
	 * 查询已经上传的分片信息的链接地址
	 */
	private static String uploadedUrl = "http://localhost:8182/uploaded/";

	/**
	 * 提请服务器合并分片文件的链接地址
	 */
	private static String mergeUrl = "http://localhost:8182/merge/";

	/**
	 * 缓存客户端未上传完毕的文件信息的缓存文件
	 */
	private static String cacheFile = "d:/test/cache/cacheFile.txt";

	/**
	 * 分片大小：1MB
	 */
	private static int partSize = 1048576;

	public static void main(String[] args) throws IOException {
		long start = System.currentTimeMillis();

//		String fullFileName = "d:/downloads/TencentMeeting_0300000000_2.3.0.426.publish.exe";
//		String fullFileName = "d:/downloads/100913505181_0手把手教你学习51单片机.docx";
		String fullFileName = "d:/downloads/20190911-语音误识别.MOV";
		File file = new File(fullFileName);
		String currentMD5 = MD5.getFileMD5String(file);
		long currentLastModified = file.lastModified();

		// 本文件是否首次上传
		boolean isFirstUpload = true;

		String fileId = null;
		String cacheFileId = null;
		String cacheMD5 = null;
		long cacheLastModified = 0;

		// 查询本次要上传的文件信息是否已经在缓存中存在
		String cacheInfo = getCacheInfo(fullFileName);
		if (cacheInfo != null) {
			String[] cache = cacheInfo.split(" ");
			cacheFileId = cache[0];
			cacheMD5 = cache[1];
			cacheLastModified = Long.parseLong(cache[2]);

			// 部分分片上传后，原始文件已经被修改过，需要删除本地缓存，当做新文件重新上传
			if (!currentMD5.equals(cacheMD5) || currentLastModified != cacheLastModified) {
				deleteCacheLine(fullFileName.replace("/", "").replace("\\", ""));
				fileId = UUID.randomUUID().toString().replace("-", "");
				// 缓存要上传的文件相关信息，如果本次上传未成功，下次可以从缓存中获取相关信息继续上传。
				saveCacheInfo(fullFileName, fileId, currentMD5, currentLastModified);
			} else {
				fileId = cacheFileId;
				isFirstUpload = false;
			}
		} else {
			fileId = UUID.randomUUID().toString().replace("-", "");
			// 缓存要上传的文件相关信息，如果本次上传未成功，下次可以从缓存中获取相关信息继续上传。
			saveCacheInfo(fullFileName, fileId, currentMD5, currentLastModified);
		}

		// 构建本次上传文件的基础信息
		FileInfo fileInfo = new FileInfo(fullFileName, fileId, partSize, currentMD5);

		// 分片索引：0代表没有上传，为1代表已经上传
		int[] partIndex = new int[fileInfo.getPartCount()];
		if (!isFirstUpload) {
			// 查询服务器上存在的分片索引的值
			partIndex = uploadedPartFileSearch(fileInfo);
		}

		// 分片文件上传
		partFileUpload(uploadUrl, fileInfo, partIndex);

		// 指示服务器合并文件
		mergePartFile(mergeUrl, fileInfo);
		SERVICE.shutdown();
		long end = System.currentTimeMillis();
		System.out.println("上传用时：" + (end - start) / 1000 + "秒");
	}

	private static String getCacheInfo(String fullFileName) throws FileNotFoundException, IOException {
		File file = new File(cacheFile);
		if (!file.exists()) {
			return null;
		}
		try (BufferedReader reader = new BufferedReader(new FileReader(file));) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith(fullFileName)) {
					return line.substring(fullFileName.length() + 1);
				}
			}
		}
		return null;
	}

	private static void saveCacheInfo(String fullFileName, String fileId, String fileMD5, long lastModified)
			throws FileNotFoundException, IOException {
		File file = new File(cacheFile);
		if (!file.exists()) {
			file.createNewFile();
		}
		try (FileOutputStream fos = new FileOutputStream(file, true);) {
			if (file.length() > 0) {
				fos.write("\r\n".getBytes());
			}
			fos.write((fullFileName.replace("/", "").replace("\\", "") + " " + fileId + " " + fileMD5 + " "
					+ lastModified).getBytes());
		}
	}

	public static void partFileUpload(String uploadUrl, FileInfo fileInfo, int[] partIndex) {
		File file = new File(fileInfo.getPath());
		try (FileInputStream fis = new FileInputStream(file);) {

			// partCount个线程全部处理完后 countDownLatch.await()阻塞通过
			CountDownLatch countDownLatch = new CountDownLatch(partIndex.length);

			for (int i = 0; i < partIndex.length; i++) {
				if (partIndex[i] == 1) {
					countDownLatch.countDown();
					continue;
				}
				FileUploadRunnable fileUploadRunnable = new FileUploadRunnable(uploadUrl, fileInfo, i, countDownLatch);
				SERVICE.submit(fileUploadRunnable);
				System.out.println("第" + i + "块已经提交线程池。");
			}
			// 阻塞直至countDownLatch.countDown()被调用partCount次 即所有线程执行任务完毕
			countDownLatch.await();
			System.out.println("分块文件全部上传完毕");
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void mergePartFile(String mergeUrl, FileInfo fileInfo) throws ClientProtocolException, IOException {
		System.out.println("开始请求后台将临时文件拼装为原始文件。");
		CloseableHttpClient ht = HttpClientBuilder.create().build();
		String param = fileInfo.getFileId() + "/" + fileInfo.getPartCount() + "/" + fileInfo.getFileName();
		HttpPost post = new HttpPost(mergeUrl + param);
		HttpResponse response = ht.execute(post);
		if (response.getStatusLine().getStatusCode() == 200) {
			// 文件上传完毕，删除cacheFile中的缓存信息
			deleteCacheLine(fileInfo.getPath());
		}
	}

	private static void deleteCacheLine(String fullFileName) throws FileNotFoundException, IOException {
		File cache = new File(cacheFile);
		File cacheTmp = new File("d:/test/cacheFile-new.txt");
		try (BufferedReader reader = new BufferedReader(new FileReader(cache));
				PrintWriter writer = new PrintWriter(cacheTmp);) {
			String line;
			while ((line = reader.readLine()) != null) {
				// 判断条件，根据自己的情况书写，会删除所有符合条件的行
				if (line.startsWith(fullFileName.replace("/", "").replace("\\", ""))) {
					line = null;
					continue;
				}
				writer.println(line);
				writer.flush();
			}
		}
		// 删除老文件
		boolean isDelete = cache.delete();
		if (isDelete) {
			cacheTmp.renameTo(cache);
		} else {
			cacheTmp.delete();
		}
	}

	public static int[] uploadedPartFileSearch(FileInfo fileInfo) throws ClientProtocolException, IOException {
		CloseableHttpClient ht = HttpClientBuilder.create().build();
		HttpPost post = new HttpPost(uploadedUrl + fileInfo.getFileId());
		HttpResponse response = ht.execute(post);
		int[] result = new int[fileInfo.getPartCount()];
		if (response.getStatusLine().getStatusCode() == 200) {
			String ret = EntityUtils.toString(response.getEntity(), "utf-8");
			if (ret != null && ret.trim().length() > 0) {
				for (int i = 0; i < result.length; i++) {
					if (ret.contains("," + i + ",")) {
						result[i] = 1;
					}
				}
			}
		}
		return result;
	}

}
