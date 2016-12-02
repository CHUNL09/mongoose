package com.emc.mongoose.storage.driver.net.http.s3;

import com.emc.mongoose.model.item.Item;
import com.emc.mongoose.model.item.ItemFactory;
import com.emc.mongoose.ui.log.LogUtil;
import com.emc.mongoose.ui.log.Markers;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.List;

import static com.emc.mongoose.storage.driver.net.http.s3.S3Constants.QNAME_IS_TRUNCATED;
import static com.emc.mongoose.storage.driver.net.http.s3.S3Constants.QNAME_ITEM;
import static com.emc.mongoose.storage.driver.net.http.s3.S3Constants.QNAME_ITEM_ID;
import static com.emc.mongoose.storage.driver.net.http.s3.S3Constants.QNAME_ITEM_SIZE;
/**
 Created by andrey on 02.12.16.
 */
public final class BucketXmlListingHandler<I extends Item>
extends DefaultHandler {

	private static final Logger LOG = LogManager.getLogger();

	private int count = 0;
	private boolean isInsideItem = false;
	private boolean itIsItemId = false;
	private boolean itIsItemSize = false;
	private boolean itIsTruncateFlag = false;
	private boolean isTruncated = false;
	private String oid = null, strSize = null;
	private long offset;
	private I nextItem;

	private final List<I> itemsBuffer;
	private final ItemFactory<I> itemFactory;
	private final int idRadix;

	public BucketXmlListingHandler(
		final List<I> itemsBuffer, final ItemFactory<I> itemFactory, final int idRadix
	) {
		this.itemsBuffer = itemsBuffer;
		this.itemFactory = itemFactory;
		this.idRadix = idRadix;
	}

	@Override
	public final void startElement(
		final String uri, final String localName, final String qName, Attributes attrs
	) throws SAXException {
		isInsideItem = isInsideItem || QNAME_ITEM.equals(qName);
		itIsItemId = isInsideItem && QNAME_ITEM_ID.equals(qName);
		itIsItemSize = isInsideItem && QNAME_ITEM_SIZE.equals(qName);
		itIsTruncateFlag = QNAME_IS_TRUNCATED.equals(qName);
		super.startElement(uri, localName, qName, attrs);
	}
	
	@Override @SuppressWarnings("unchecked")
	public final void endElement(
		final String uri, final String localName, final String qName
	) throws SAXException {
		
		itIsItemId = itIsItemId && !QNAME_ITEM_ID.equals(qName);
		itIsItemSize = itIsItemSize && !QNAME_ITEM_SIZE.equals(qName);
		itIsTruncateFlag = itIsTruncateFlag && !QNAME_IS_TRUNCATED.equals(qName);
		
		if(isInsideItem && QNAME_ITEM.equals(qName)) {
			isInsideItem = false;
			
			long size = -1;
			
			if(strSize != null && strSize.length() > 0) {
				try {
					size = Long.parseLong(strSize);
				} catch(final NumberFormatException e) {
					LogUtil.exception(
						LOG, Level.WARN, e, "Data object size should be a 64 bit number"
					);
				}
			} else {
				LOG.trace(Markers.ERR, "No \"{}\" element or empty", QNAME_ITEM_SIZE);
			}
			
			if(oid != null && oid.length() > 0 && size > -1) {
				try {
					offset = Long.parseLong(oid, idRadix);
				} catch(final NumberFormatException e) {
					LogUtil.exception(
						LOG, Level.WARN, e, "Failed to parse the item id \"{}\"", oid
					);
					offset = 0;
				}
				nextItem = itemFactory.getItem(oid, offset, size);
				itemsBuffer.add(nextItem);
				count ++;
			} else {
				LOG.trace(Markers.ERR, "Invalid object id ({}) or size ({})", oid, strSize);
			}
		}
		
		super.endElement(uri, localName, qName);
	}
	
	@Override
	public final void characters(final char buff[], final int start, final int length)
	throws SAXException {
		if(itIsItemId) {
			oid = new String(buff, start, length);
		} else if(itIsItemSize) {
			strSize = new String(buff, start, length);
		} else if(itIsTruncateFlag) {
			isTruncated = Boolean.parseBoolean(new String(buff, start, length));
		}
		super.characters(buff, start, length);
	}
}