package com.test.upload.slice;

import java.io.File;
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

	// 文件id
	private String fileId;

	// 分块编号
	private int index;

	private CountDownLatch countDownLatch;

	// 当前分段大小
	private long partSize;

	// 当前分段在输入流中的起始位置
	private long partStart;

	// 总文件
	private File file;

	public FileUploadRunnable(String url, String fileId, int index, CountDownLatch countDownLatch, File file,
			long partSize, long partStart) {
		this.url = url;
		this.fileId = fileId;
		this.index = index;
		this.countDownLatch = countDownLatch;
		this.partSize = partSize;
		this.partStart = partStart;
		this.file = file;
	}

	public void run() {
		System.out.println("第" + index + "个线程已经开始运行。");
		try (FileInputStream fis = new FileInputStream(file);
				CloseableHttpClient ht = HttpClientBuilder.create().build();) {
			String tempFileName = "temp_" + fileId + "_" + index + ".temp";
			// 请求接收分段上传的地址
			String u = url + partSize + "/" + tempFileName;
			System.out.println(u);
			HttpPost post = new HttpPost(u);
			HttpResponse response;
			// 跳过起始位置
			fis.skip(partStart);

			System.out.println("开始上传分块:" + index);

			ByteBuffer bb = ByteBuffer.allocate((int)partSize);
			int len = fis.getChannel().read(bb);
			System.out.println("partSize====" + partSize);
			System.out.println("len====" + len);
			bb.flip();
			System.out.println("limit====" + bb.limit());
			byte[] resultBytes = new byte[bb.limit()];
			bb.get(resultBytes);
			ByteArrayEntity byteArrayEntity = new ByteArrayEntity(resultBytes, ContentType.APPLICATION_OCTET_STREAM);

			post.setEntity(byteArrayEntity);

//			post.setEntity(new InputStreamEntity(fis, fis.getChannel().size()));
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
