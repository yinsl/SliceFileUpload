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
import com.test.upload.slice.dto.SliceUploadFileInfo;
import com.test.upload.slice.utils.MD5;

/**
 * 大文件分片上传客户端
 *
 */
public class SliceUploadClient {

	/**
	 * 文件分片上传的授权信息的链接地址
	 */
	private static String uploadAuthUrl = "http://localhost:8182/uploadAuth/";

	/**
	 * 分片文件上传的链接地址
	 */
	private static String uploadUrl = "http://localhost:8182/upload/";

	/**
	 * 查询未上传的分片信息的链接地址
	 */
	private static String unUploadedUrl = "http://localhost:8182/unUploaded/";

	/**
	 * 缓存客户端未上传完毕的文件信息的缓存文件
	 */
	private static String cacheFile = "d:\\test\\cache\\cacheFile.txt";

	public static void main(String[] args) throws IOException {
		
		// 历史文件上传，每次联网都要重新查看本地缓存，看是否有没有上传完毕的文件。
		historyFileUpload();

		// 新文件上传
		newFileUpload();
	}
	
	/**
	 * 文件首次上传处理
	 * @throws IOException
	 */
	private static void newFileUpload() throws IOException {
		long start = System.currentTimeMillis();
		
		// 新文件上传
				String path = "d:\\downloads\\TencentMeeting_0300000000_2.3.0.426.publish.exe";
//				String path = "d:\\downloads\\100913505181_0手把手教你学习51单片机.docx";
//				String path = "d:\\downloads\\20190911-语音误识别.MOV";
//				String path = "d:\\downloads\\国六车接入详细设计文档 - 副本.docx";
				File file = new File(path);
				String currentMD5 = MD5.getFileMD5String(file);
				long currentLastModified = file.lastModified();

				String eventId = null;

				SliceUploadFileInfo sliceUploadFileInfo = null;

				// 新文件上传
				sliceUploadFileInfo = getUploadAuth(file.getName(), currentMD5, file.length());
				if (sliceUploadFileInfo != null) {
					eventId = sliceUploadFileInfo.getEventId();
					CacheInfo cacheInfo = new CacheInfo(eventId, currentMD5, currentLastModified, path);
					// 缓存要上传的文件相关信息，如果本次上传未成功，下次可以从缓存中获取相关信息继续上传。
					saveCacheInfo(cacheInfo);
				} else {
					// 云端出错
					return;
				}

				// 分片文件上传
				sliceFileUpload(uploadUrl, file, sliceUploadFileInfo.getUnUploadedIndexes(), sliceUploadFileInfo);

				long end = System.currentTimeMillis();
				System.out.println("新文件上传用时：" + (end - start) / 1000 + "秒");
	}

	/**
	 * 未上传完的所有文件都在此继续上传
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private static void historyFileUpload() throws FileNotFoundException, IOException {
		long start = System.currentTimeMillis();
		
		SliceUploadFileInfo sliceUploadFileInfo = null;

		// 查询本地缓存中未上传的所有文件信息
		List<CacheInfo> cacheInfoList = getCacheInfo();
		if (cacheInfoList != null && cacheInfoList.size() > 0) {
			for (int i = 0; i < cacheInfoList.size(); i++) {
				CacheInfo cacheInfo = cacheInfoList.get(i);

				String path = cacheInfo.getPath();

				File file = new File(path);
				String currentMD5 = MD5.getFileMD5String(file);
				long currentLastModified = file.lastModified();

				// 部分分片上传后，原始文件已经被修改过，需要删除本地缓存，重新上传整个文件
				if (!currentMD5.equals(cacheInfo.getMd5()) || currentLastModified != cacheInfo.getLastModified()) {
					//删除本地缓存
					deleteCacheLine(cacheInfo.getEventId());
					// 新文件上传
					sliceUploadFileInfo = getUploadAuth(file.getName(), currentMD5, file.length());
					if (sliceUploadFileInfo != null) {
						// 缓存要上传的文件相关信息，如果本次上传未成功，下次可以从缓存中获取相关信息继续上传。
						cacheInfo.setMd5(currentMD5);
						cacheInfo.setLastModified(currentLastModified);
						saveCacheInfo(cacheInfo);
						// 分片文件上传
						sliceFileUpload(uploadUrl, file, sliceUploadFileInfo.getUnUploadedIndexes(),
								sliceUploadFileInfo);
					} else {
						// 云端出错
						continue;
					}
				} else {
					// 文件在上次上传一部分后没有被修改，可以继续上传
					
					// 查询服务器上的文件缓存信息
					sliceUploadFileInfo = unUploadedSliceFileSearch(cacheInfo.getEventId());
					
					if (sliceUploadFileInfo == null) {
						// 该文件已经超过允许的上传时间，整个文件按上传失败处理
						continue;
					}
					// 分片文件上传
					sliceFileUpload(uploadUrl, file, sliceUploadFileInfo.getUnUploadedIndexes(), sliceUploadFileInfo);
				}
			}
		}
		long end = System.currentTimeMillis();
		System.out.println("历史文件上传用时：" + (end - start) / 1000 + "秒");
	}

	private static List<CacheInfo> getCacheInfo() throws FileNotFoundException, IOException {
		File file = new File(cacheFile);
		if (!file.exists()) {
			return null;
		}
		List<CacheInfo> cacheInfoList = new ArrayList<>();
		;
		try (BufferedReader reader = new BufferedReader(new FileReader(file));) {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] cache = line.split(" ");
				String path = "";//处理文件名中的空格
				for (int i = 3; i < cache.length; i++) {

					if (i == cache.length - 1) {
						path += cache[i];
					} else {
						path += cache[i] + " ";
					}
				}
				CacheInfo cacheInfo = new CacheInfo(cache[0], cache[1], Long.parseLong(cache[2]), path);
				cacheInfoList.add(cacheInfo);
			}
		}
		return cacheInfoList;
	}

	/**
	 * cache格式：eventId md5 lastmodified path
	 * 
	 * @param path
	 * @param eventId
	 * @param fileMD5
	 * @param lastModified
	 * @param authInfo
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private static void saveCacheInfo(CacheInfo cacheInfo) throws FileNotFoundException, IOException {
		File file = new File(cacheFile);
		if (!file.exists()) {
			file.createNewFile();
		}
		try (FileOutputStream fos = new FileOutputStream(file, true);) {
			if (file.length() > 0) {
				fos.write("\r\n".getBytes());
			}
			fos.write((cacheInfo.getEventId() + " " + cacheInfo.getMd5() + " " + cacheInfo.getLastModified() + " " + cacheInfo.getPath().replace("\\", "/")).getBytes());
		}
	}

	public static void sliceFileUpload(String uploadUrl, File file, int[] unUploadedSliceIndexes,
			SliceUploadFileInfo authInfo) {

		// 上传分片文件的线程池
		ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		try (FileInputStream fis = new FileInputStream(file);) {

			// needUploadSliceIndex个线程全部处理完后 countDownLatch.await()阻塞通过
			CountDownLatch countDownLatch = new CountDownLatch(unUploadedSliceIndexes.length);

			for (int i = 0; i < unUploadedSliceIndexes.length; i++) {
				FileUploadRunnable fileUploadRunnable = new FileUploadRunnable(uploadUrl, file,
						unUploadedSliceIndexes[i], countDownLatch, authInfo);
				service.submit(fileUploadRunnable);
				System.out.println("第" + unUploadedSliceIndexes[i] + "块已经提交线程池。");
			}
			// 阻塞直至countDownLatch.countDown()被调用needUploadSliceIndex次 即所有线程执行任务完毕
			countDownLatch.await();
			deleteCacheLine(authInfo.getEventId());
			System.out.println("分块文件全部上传完毕");
			service.shutdown();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static SliceUploadFileInfo unUploadedSliceFileSearch(String eventId)
			throws ClientProtocolException, IOException {
		CloseableHttpClient ht = HttpClientBuilder.create().build();
		HttpPost post = new HttpPost(unUploadedUrl + eventId);
		HttpResponse res = ht.execute(post);
		if (res.getStatusLine().getStatusCode() == 200) {
			String ret = EntityUtils.toString(res.getEntity(), "utf-8");
			Gson gson = new Gson();
			return gson.fromJson(ret, SliceUploadFileInfo.class);
		}
		return null;
	}

	public static SliceUploadFileInfo getUploadAuth(String fileName, String fileMD5, long fileSize)
			throws ClientProtocolException, IOException {
		CloseableHttpClient ht = HttpClientBuilder.create().build();
		String param = fileMD5 + "/" + fileSize + "/" + fileName;
		HttpPost post = new HttpPost(uploadAuthUrl + param.replace(" ", "%20"));
		HttpResponse res = ht.execute(post);
		if (res.getStatusLine().getStatusCode() == 200) {
			String ret = EntityUtils.toString(res.getEntity(), "utf-8");
			Gson gson = new Gson();
			return gson.fromJson(ret, SliceUploadFileInfo.class);
		}
		return null;
	}

	private static void deleteCacheLine(String eventId) throws FileNotFoundException, IOException {
		File cache = new File(cacheFile);
		File cacheTmp = new File("d:/test/cacheFile-new.txt");
		try (BufferedReader reader = new BufferedReader(new FileReader(cache));
				PrintWriter writer = new PrintWriter(cacheTmp);) {
			String line;
			while ((line = reader.readLine()) != null) {
				// 判断条件，根据自己的情况书写，会删除所有符合条件的行
				if (line.startsWith(eventId)) {
					line = null;
					continue;
				}
				writer.println(line);
			}
			writer.flush();
		}
		// 删除老文件
		boolean isDelete = cache.delete();
		if (isDelete) {
			cacheTmp.renameTo(cache);
		} else {
			cacheTmp.delete();
		}
	}

}
