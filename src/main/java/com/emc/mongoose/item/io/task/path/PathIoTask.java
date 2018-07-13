package com.emc.mongoose.item.io.task.path;

import com.emc.mongoose.item.io.task.IoTask;
import com.emc.mongoose.item.PathItem;

/**
 Created by kurila on 30.01.17.
 */
public interface PathIoTask<I extends PathItem>
extends IoTask<I> {

	@Override
	I item();
	
	long getCountBytesDone();
	
	void setCountBytesDone(long n);
	
	long getRespDataTimeStart();
	
	void startDataResponse();
}