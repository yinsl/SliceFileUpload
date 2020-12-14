package com.test.upload.slice;

public class CacheInfo {
	
	private String eventId;
	
	private String path;
	
	private String md5;
	
	private long lastModified;

	public CacheInfo(String eventId, String md5,long lastModified, String path) {
		this.eventId = eventId;
		this.path = path;
		this.md5 = md5;
		this.lastModified = lastModified;
	}

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}

	public String getMd5() {
		return md5;
	}

	public void setMd5(String md5) {
		this.md5 = md5;
	}

	public long getLastModified() {
		return lastModified;
	}

	public void setLastModified(long lastModified) {
		this.lastModified = lastModified;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}
	
}
