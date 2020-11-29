package com.test.upload.slice;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import com.test.upload.slice.utils.MD5;

public class SliceUploadClient {

	public static void main(String[] args) throws IOException {
		String uploadUrl = "http://localhost:8182/upload/";
		String mergeUrl = "http://localhost:8182/merge/";
//		String filename="TencentMeeting_0300000000_2.3.0.426.publish.exe";
		String filename="国六车接入详细设计文档.docx";
		long start = System.currentTimeMillis();
		partFileUpload("d:/downloads/", uploadUrl,mergeUrl, filename);
		long end = System.currentTimeMillis();
		System.out.println("上传用时：" + (end - start) / 1000 + "秒");
	}

	public static void partFileUpload(String dirPath, String uploadUrl, String mergeUrl, String fileName) {
		File file = new File(dirPath + fileName);
		try (FileInputStream fis = new FileInputStream(file);) {
			// 分段大小 1MB为一段
			long partSize = 1024 * 1024;
			// 获取总文件大小
			long fileSize = fis.getChannel().size();
			// 分多少段
			int partCount = (int) (fileSize / partSize);
			if (partSize * partCount < fileSize) {
				partCount++;
			}
			System.out.println("文件一共分为" + partCount + "块");
			// 线程大小为分段数
			ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
			// partCount个线程全部处理完后 countDownLatch.await()阻塞通过
			CountDownLatch countDownLatch = new CountDownLatch(partCount);
			// 获取文件ID
			String fileId = MD5.getFileMD5String(file);
			System.out.println("fileId: " + fileId);
			for (int i = 0; i < partCount; i++) {
				// 当前分段起始位置
				long partStart = i * partSize;
				// 当前分段大小 如果为最后一个大小为fileSize-partStart 其他为partSize
				long curPartSize = (i + 1 == partCount) ? (fileSize - partStart) : partSize;
				FileUploadRunnable fileUploadRunnable = new FileUploadRunnable(uploadUrl, fileId, i, countDownLatch, file,
						curPartSize, partStart);
				service.submit(fileUploadRunnable);
				System.out.println("第"+ i + "块已经提交线程池。");
//				TimeUnit.SECONDS.sleep(10);
			}
			// 阻塞直至countDownLatch.countDown()被调用partCount次 即所有线程执行任务完毕
			countDownLatch.await();
			service.shutdown();
			System.out.println("分块文件全部上传完毕");
			System.out.println("开始请求后台将临时文件拼装为源文件。");
			CloseableHttpClient ht = HttpClientBuilder.create().build();
			String param = fileId + "/" + partCount + "/" + fileName;
			HttpPost post = new HttpPost(mergeUrl + param);
			HttpResponse response = ht.execute(post);
			if (response.getStatusLine().getStatusCode() == 200) {
				String ret = EntityUtils.toString(response.getEntity(), "utf-8");
				System.out.println(ret);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


}
