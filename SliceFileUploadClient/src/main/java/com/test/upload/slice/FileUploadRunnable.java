package com.test.upload.slice;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

public class FileUploadRunnable implements Runnable {

	private String url;

	// 分块编号
	private int index;

	private CountDownLatch countDownLatch;
	
	private FileInfo fileInfo;
	
	public FileUploadRunnable(String url, FileInfo fileInfo, int index, CountDownLatch countDownLatch) {
		this.url = url;
		this.index = index;
		this.countDownLatch = countDownLatch;
		this.fileInfo = fileInfo;
	}

	public void run() {
		System.out.println("第" + index + "个线程已经开始运行。");
		try (FileInputStream fis = new FileInputStream(fileInfo.getPath());
				CloseableHttpClient ht = HttpClientBuilder.create().build();) {
			
			HttpResponse response;
			long partStart = index * fileInfo.getPartSize();
			// 跳过起始位置
			fis.skip(partStart);

			System.out.println("开始上传分块:" + index);
			
			// 当前分段大小 如果为最后一个大小为fileSize-partStart 其他为partSize
			long curPartSize = (index + 1 == fileInfo.getPartCount()) ? (fileInfo.getFileSize()- partStart) : fileInfo.getPartSize();

			ByteBuffer bb = ByteBuffer.allocate((int)curPartSize);
			int len = fis.getChannel().read(bb);
			System.out.println("curPartSize====" + curPartSize);
			System.out.println("len====" + len);
			bb.flip();
			System.out.println("limit====" + bb.limit());
			byte[] resultBytes = new byte[bb.limit()];
			bb.get(resultBytes);
			ByteArrayEntity byteArrayEntity = new ByteArrayEntity(resultBytes, ContentType.APPLICATION_OCTET_STREAM);

			// 请求接收分段上传的地址
			String u = url + fileInfo.getFileId() + "/" + fileInfo.getPartCount() + "/" + fileInfo.getPartSize() + "/" + index;
			System.out.println(u);
			HttpPost post = new HttpPost(u);
			
			post.setEntity(byteArrayEntity);

			response = ht.execute(post);
			if (response.getStatusLine().getStatusCode() == 200) {
				String ret = EntityUtils.toString(response.getEntity(), "utf-8");
				System.out.println(ret);
				System.out.println("分块" + index + "上传完毕");

			} else {
				System.out.println(response.getStatusLine().getStatusCode());
			}
		} catch (Exception e) {
			System.out.println("第" + index + "个线程运行出现异常。");
			e.printStackTrace();
		}
		countDownLatch.countDown();
		System.out.println("第" + index + "个线程已经运行完毕。");
	}
}
