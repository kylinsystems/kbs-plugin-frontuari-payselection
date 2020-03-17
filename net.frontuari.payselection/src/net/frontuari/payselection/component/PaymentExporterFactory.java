package net.frontuari.payselection.component;

import net.frontuari.payselection.base.FTUPaymentExporterFactory;
import net.frontuari.utils.B_BancaribePE;
import net.frontuari.utils.B_BanescoPE;
import net.frontuari.utils.B_BanplusPE;
import net.frontuari.utils.B_ExteriorPE;
import net.frontuari.utils.B_MercantilPE;
import net.frontuari.utils.B_ProvincialPE;
import net.frontuari.utils.B_VenezuelaPE;

public class PaymentExporterFactory extends FTUPaymentExporterFactory {

	@Override
	protected void initialize() {
		registerPaymentExporter(B_BanescoPE.class);
		registerPaymentExporter(B_BanplusPE.class);
		registerPaymentExporter(B_ExteriorPE.class);
		registerPaymentExporter(B_MercantilPE.class);
		registerPaymentExporter(B_ProvincialPE.class);
		registerPaymentExporter(B_VenezuelaPE.class);
		registerPaymentExporter(B_BancaribePE.class);
	}

}
