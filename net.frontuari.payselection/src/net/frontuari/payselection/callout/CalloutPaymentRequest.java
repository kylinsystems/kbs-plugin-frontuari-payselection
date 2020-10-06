package net.frontuari.payselection.callout;

import java.util.Optional;

import org.compiere.model.MDocType;

import net.frontuari.payselection.base.FTUCallout;
import net.frontuari.payselection.model.MFTUPaymentRequest;

public class CalloutPaymentRequest extends FTUCallout {

	public CalloutPaymentRequest() {
	}

	@Override
	protected String start() {
		if(getColumnName().equals(MFTUPaymentRequest.COLUMNNAME_C_DocType_ID))
		{
			int C_DocType_ID = Optional.ofNullable((Integer) getTab().getValue(MFTUPaymentRequest.COLUMNNAME_C_DocType_ID))
					.orElse(0);
			MDocType dt = new MDocType(getCtx(), C_DocType_ID, null);
			
			if(dt.get_ID() == 0)
				return null;
			
			setValue(MFTUPaymentRequest.COLUMNNAME_RequestType, dt.get_ValueAsString("RequestType"));
		}
		return null;
	}

}
