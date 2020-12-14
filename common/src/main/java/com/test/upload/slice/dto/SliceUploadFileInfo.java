package com.test.upload.slice.dto;

public class SliceUploadFileInfo {

	private String eventId;

	private String aeskey;

	private String token;

	/**
	 * 分片大小，默认1MB
	 */
	private int sliceSize = 1048576;

	/**
	 * 未上传的分片索引
	 */
	private int[] unUploadedIndexes;

	public SliceUploadFileInfo(String eventId, String aeskey, String token, int sliceSize,
			int[] unUploadedIndexes) {
		this.eventId = eventId;
		this.aeskey = aeskey;
		this.token = token;
		this.sliceSize = sliceSize;
		this.unUploadedIndexes = unUploadedIndexes;
	}

	public int getSliceSize() {
		return sliceSize;
	}

	public void setSliceSize(int sliceSize) {
		this.sliceSize = sliceSize;
	}

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}

	public String getAeskey() {
		return aeskey;
	}

	public void setAeskey(String aeskey) {
		this.aeskey = aeskey;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public int[] getUnUploadedIndexes() {
		return unUploadedIndexes;
	}

	public void setUnUploadedIndexes(int[] unUploadedIndexes) {
		this.unUploadedIndexes = unUploadedIndexes;
	}

}
