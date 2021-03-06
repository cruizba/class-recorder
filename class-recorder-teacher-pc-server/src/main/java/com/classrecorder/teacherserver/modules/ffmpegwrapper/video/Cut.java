package com.classrecorder.teacherserver.modules.ffmpegwrapper.video;


public class Cut {
	
	private String start;
	private String end;
	
	public Cut() {
		this.start = "00:00:00";
		this.end = "00:00:00";
	}
	
	public Cut(String start, String end) {
		this.start = start;
		this.end = end;
	}
	
	public String getStart() {
		return start;
	}

	public void setStart(String start) {
		this.start = start;
	}

	public String getEnd() {
		return end;
	}

	public void setEnd(String end) {
		this.end = end;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((end == null) ? 0 : end.hashCode());
		result = prime * result + ((start == null) ? 0 : start.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Cut other = (Cut) obj;
		if (end == null) {
			if (other.end != null)
				return false;
		} else if (!end.equals(other.end))
			return false;
		if (start == null) {
			if (other.start != null)
				return false;
		} else if (!start.equals(other.start))
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return "Start: " + start + " | End: " + end;
	}
	
	

}
