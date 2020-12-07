package com.test.upload.slice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;
import com.test.upload.slice.dto.UploadAuthInfo;
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
	 * 文件授权信息的链接地址
	 */
	private static String uploadAuthUrl = "http://localhost:8182/uploadAuth/";

	/**
	 * 分片文件上传的链接地址
	 */
	private static String uploadUrl = "http://localhost:8182/upload/";

	/**
	 * 查询已经上传的分片信息的链接地址
	 */
	private static String uploadedUrl = "http://localhost:8182/uploaded/";

	/**
	 * 缓存客户端未上传完毕的文件信息的缓存文件
	 */
	private static String cacheFile = "d:\\test\\cache\\cacheFile.txt";

	public static void main(String[] args) throws IOException {
		long start = System.currentTimeMillis();

//		String fullFileName = "d:\\downloads\\TencentMeeting_0300000000_2.3.0.426.publish.exe";
//		String fullFileName = "d:\\downloads\\100913505181_0手把手教你学习51单片机.docx";
		String fullFileName = "d:\\downloads\\20190911-语音误识别.MOV";
		File file = new File(fullFileName);
		String currentMD5 = MD5.getFileMD5String(file);
		long currentLastModified = file.lastModified();

		String eventId = null;
		String cacheEventId = null;
		String cacheMD5 = null;
		long cacheLastModified = 0;
		int cacheSliceSize = 0;
		String cacheAesKey = null;
		String cacheToken = null;

		UploadAuthInfo authInfo = null;

		// 查询本次要上传的文件信息是否已经在缓存中存在
		String cacheInfo = getCacheInfo(fullFileName);
		if (cacheInfo != null) {
			String[] cache = cacheInfo.split(" ");
			cacheEventId = cache[0];
			cacheMD5 = cache[1];
			cacheLastModified = Long.parseLong(cache[2]);
			cacheAesKey = cache[3];
			cacheToken = cache[4];
			cacheSliceSize = Integer.parseInt(cache[5]);

			// 部分分片上传后，原始文件已经被修改过，需要删除本地缓存，当做新文件重新上传
			if (!currentMD5.equals(cacheMD5) || currentLastModified != cacheLastModified) {
				deleteCacheLine(fullFileName);
				authInfo = getUploadAuth(file.getName(), currentMD5, file.length());
				if (authInfo != null) {
					eventId = authInfo.getEventId();
					// 缓存要上传的文件相关信息，如果本次上传未成功，下次可以从缓存中获取相关信息继续上传。
					saveCacheInfo(fullFileName, eventId, currentMD5, currentLastModified, authInfo);
				} else {
					return;
				}
			} else {
				eventId = cacheEventId;
				authInfo = new UploadAuthInfo(eventId, cacheAesKey, cacheToken, cacheSliceSize);
			}
		} else {
			authInfo = getUploadAuth(file.getName(), currentMD5, file.length());
			if (authInfo != null) {
				eventId = authInfo.getEventId();
				// 缓存要上传的文件相关信息，如果本次上传未成功，下次可以从缓存中获取相关信息继续上传。
				saveCacheInfo(fullFileName, eventId, currentMD5, currentLastModified, authInfo);
			} else {
				return;
			}
		}

		int totalSlices = getTotalSlices(file.length(), authInfo.getSliceSize());

		// 分片索引：0代表没有上传，为1代表已经上传
		List<Integer> needUploadSliceIndex = null;

		// 查询服务器上存在的分片索引的值
		needUploadSliceIndex = uploadedSliceFileSearch(eventId, totalSlices);

		// 分片文件上传
		sliceFileUpload(uploadUrl, file, needUploadSliceIndex, authInfo);

		SERVICE.shutdown();
		long end = System.currentTimeMillis();
		System.out.println("上传用时：" + (end - start) / 1000 + "秒");
	}

	private static int getTotalSlices(long fileSize, int sliceSize) {
		int totalSlices = 0;

		// 分多少段
		totalSlices = (int) (fileSize / sliceSize);
		if (sliceSize * totalSlices < fileSize) {
			totalSlices++;
		}
		return totalSlices;
	}

	private static String getCacheInfo(String fullFileName) throws FileNotFoundException, IOException {
		File file = new File(cacheFile);
		if (!file.exists()) {
			return null;
		}
		String tempFileName = fullFileName.replace("/", "").replace("\\", "");
		try (BufferedReader reader = new BufferedReader(new FileReader(file));) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.endsWith(tempFileName)) {
					int index = line.indexOf(tempFileName);
					return line.substring(0, index - 1);
				}
			}
		}
		return null;
	}

	private static void saveCacheInfo(String fullFileName, String eventId, String fileMD5, long lastModified,
			UploadAuthInfo authInfo) throws FileNotFoundException, IOException {
		File file = new File(cacheFile);
		if (!file.exists()) {
			file.createNewFile();
		}
		try (FileOutputStream fos = new FileOutputStream(file, true);) {
			if (file.length() > 0) {
				fos.write("\r\n".getBytes());
			}
			fos.write((eventId + " " + fileMD5 + " " + lastModified + " " + authInfo.getAeskey() + " "
					+ authInfo.getToken() + " " + authInfo.getSliceSize() + " "
					+ fullFileName.replace("/", "").replace("\\", "")).getBytes());
		}
	}

	public static void sliceFileUpload(String uploadUrl, File file, List<Integer> needUploadSliceIndex, UploadAuthInfo authInfo) {
		try (FileInputStream fis = new FileInputStream(file);) {

			// needUploadSliceIndex个线程全部处理完后 countDownLatch.await()阻塞通过
			CountDownLatch countDownLatch = new CountDownLatch(needUploadSliceIndex.size());

			for (int i = 0; i < needUploadSliceIndex.size(); i++) {
				FileUploadRunnable fileUploadRunnable = new FileUploadRunnable(uploadUrl, file, needUploadSliceIndex.get(i),
						countDownLatch, authInfo);
				SERVICE.submit(fileUploadRunnable);
				System.out.println("第" + needUploadSliceIndex.get(i) + "块已经提交线程池。");
			}
			// 阻塞直至countDownLatch.countDown()被调用needUploadSliceIndex次 即所有线程执行任务完毕
			countDownLatch.await();
			deleteCacheLine(file.getPath());
			System.out.println("分块文件全部上传完毕");
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static UploadAuthInfo getUploadAuth(String fileName, String fileMD5, long fileSize)
			throws ClientProtocolException, IOException {
		CloseableHttpClient ht = HttpClientBuilder.create().build();
		String param = fileMD5 + "/" + fileSize + "/" + fileName;
		HttpPost post = new HttpPost(uploadAuthUrl + param);
		HttpResponse res = ht.execute(post);
		if (res.getStatusLine().getStatusCode() == 200) {
			String ret = EntityUtils.toString(res.getEntity(), "utf-8");
			Gson gson = new Gson();
			return gson.fromJson(ret, UploadAuthInfo.class);
		}
		return null;
	}

	private static void deleteCacheLine(String fullFileName) throws FileNotFoundException, IOException {
		File cache = new File(cacheFile);
		File cacheTmp = new File("d:/test/cacheFile-new.txt");
		try (BufferedReader reader = new BufferedReader(new FileReader(cache));
				PrintWriter writer = new PrintWriter(cacheTmp);) {
			String line;
			while ((line = reader.readLine()) != null) {
				// 判断条件，根据自己的情况书写，会删除所有符合条件的行
				if (line.endsWith(fullFileName.replace("/", "").replace("\\", ""))) {
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

	public static List<Integer> uploadedSliceFileSearch(String eventId, int totalSlices)
			throws ClientProtocolException, IOException {
		CloseableHttpClient ht = HttpClientBuilder.create().build();
		HttpPost post = new HttpPost(uploadedUrl + eventId);
		HttpResponse response = ht.execute(post);
		List<Integer> result = new ArrayList<>();
		if (response.getStatusLine().getStatusCode() == 200) {
			String ret = EntityUtils.toString(response.getEntity(), "utf-8");
			if (ret != null && ret.trim().length() > 0) {
				for (int i = 0; i < totalSlices; i++) {
					if (!ret.contains("," + i + ",")) {
						result.add(i);
					}
				}
			} else {
				for (int i = 0; i < totalSlices; i++) {
					result.add(i);
				}
			}
		}
		return result;
	}

}
