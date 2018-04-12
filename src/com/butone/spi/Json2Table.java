package com.butone.spi;

import com.justep.system.data.Table;

public interface Json2Table {
	Table transform(Object json);
}
