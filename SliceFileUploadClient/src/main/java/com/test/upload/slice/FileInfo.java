package com.test.upload.slice;

import java.io.File;
import java.io.IOException;

public class FileInfo {

	private String path;

	private String fileId;

	private String fileName;

	private long fileSize;

	private int partCount;

	private int partSize;

	/**
	 * dirPath + fileName
	 */
	private String fullFileName;

	private long lastModified;

	private String fileMD5;

	public FileInfo(String fullFileName, String fileId, int partSize, String fileMD5) throws IOException {
		File file = new File(fullFileName);

		this.fullFileName = fullFileName;
		this.lastModified = file.lastModified();
		this.fileMD5 = fileMD5;
		this.path = file.getPath();
		this.fileId = fileId;
		this.fileName = file.getName();
		this.partSize = partSize;

		int partCount = 0;

		// 获取总文件大小
		this.fileSize = file.length();

		// 分多少段
		partCount = (int) (fileSize / partSize);
		if (partSize * partCount < fileSize) {
			partCount++;
		}
		this.partCount = partCount;
		System.out.println("文件一共分为" + partCount + "块");
	}

	public String getFileMD5() {
		return fileMD5;
	}

	public void setFileMD5(String fileMD5) {
		this.fileMD5 = fileMD5;
	}

	public long getLastModified() {
		return lastModified;
	}

	public void setLastModified(long lastModified) {
		this.lastModified = lastModified;
	}

	public String getFullFileName() {
		return fullFileName;
	}

	public void setFullFileName(String fullFileName) {
		this.fullFileName = fullFileName;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public int getPartSize() {
		return partSize;
	}

	public void setPartSize(int partSize) {
		this.partSize = partSize;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getFileId() {
		return fileId;
	}

	public void setFileId(String fileId) {
		this.fileId = fileId;
	}

	public long getFileSize() {
		return fileSize;
	}

	public void setFileSize(long fileSize) {
		this.fileSize = fileSize;
	}

	public int getPartCount() {
		return partCount;
	}

	public void setPartCount(int partCount) {
		this.partCount = partCount;
	}

}
