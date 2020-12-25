package com.test.upload.slice.constant;

public enum UploadStatus {

	SUCCESS("上传成功", 0),UPLOADING("上传中", 1), FAIL("上传失败", 2), NOTFOUND("上传事件不存在", 3);

	private String name;

	private int value;

	private UploadStatus(String name, int value) {
		this.name = name;
		this.value = value;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
	}

}
