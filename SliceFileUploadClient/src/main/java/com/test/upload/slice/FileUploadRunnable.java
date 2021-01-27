package com.test.upload.slice;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.util.encoders.Base64;

import com.test.upload.slice.dto.SliceUploadFileInfo;
import com.test.upload.slice.utils.AESUtil;

public class FileUploadRunnable implements Runnable {

	private String url;

	// 分块编号
	private int index;

	private CountDownLatch countDownLatch;
	
	private File file;

	private SliceUploadFileInfo authInfo;
	
	private String bizType;

	public FileUploadRunnable(String url, File file, int index, CountDownLatch countDownLatch,
			SliceUploadFileInfo authInfo, String bizType) {
		this.url = url;
		this.index = index;
		this.countDownLatch = countDownLatch;
		this.authInfo = authInfo;
		this.file = file;
		this.bizType = bizType;
	}

	public void run() {
		System.out.println("第" + index + "个线程已经开始运行。");
		try (FileInputStream fis = new FileInputStream(file.getPath());
				CloseableHttpClient ht = HttpClientBuilder.create().build();) {

			HttpResponse response;
			long sliceStart = index * authInfo.getSliceSize();
			// 跳过起始位置
			fis.skip(sliceStart);

			System.out.println("开始上传分块:" + index);
			
			int totalSlices = getTotalSlices(file.length(), authInfo.getSliceSize());

			// 当前分段大小 如果为最后一个大小为fileSize-sliceStart 其他为sliceSize
			long curSliceSize = (index + 1 == totalSlices) ? (file.length() - sliceStart)
					: authInfo.getSliceSize();

			ByteBuffer bb = ByteBuffer.allocate((int) curSliceSize);
			int len = fis.getChannel().read(bb);
			System.out.println("curSliceSize====" + curSliceSize);
			System.out.println("len====" + len);
			bb.flip();
			System.out.println("limit====" + bb.limit());
			String uuid = UUID.randomUUID().toString().replace("-", "");
			long clientTimestamp = System.currentTimeMillis();
			byte[] tmp = (uuid+clientTimestamp).getBytes();
			byte[] resultBytes = new byte[bb.limit() + tmp.length];
			bb.get(resultBytes, 0, bb.limit());
			System.arraycopy(tmp, 0, resultBytes, bb.limit(), tmp.length);
			AESUtil aes = new AESUtil();
			byte[] res = aes.encrypt(resultBytes, Base64.decode(authInfo.getAeskey()));
			
			ByteArrayEntity byteArrayEntity = new ByteArrayEntity(res, ContentType.APPLICATION_OCTET_STREAM);

			// 请求接收分段上传的地址
			String u = url + index + "/" + res.length + "/" + uuid + "/" + clientTimestamp;
			HttpPost post = new HttpPost(u);

			post.setHeader("eventId", authInfo.getEventId());
			post.setHeader("token", authInfo.getToken());
			post.setHeader("bizType", bizType);

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
	
	private static int getTotalSlices(long fileSize, int sliceSize) {
		int totalSlices = 0;

		// 分多少段
		totalSlices = (int) (fileSize / sliceSize);
		if (sliceSize * totalSlices < fileSize) {
			totalSlices++;
		}
		return totalSlices;
	}
}
