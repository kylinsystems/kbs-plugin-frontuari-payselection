/**
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Copyright (C) 2020 Frontuari, C.A. <http://frontuari.net> and contributors (see README.md file).
 */
package net.frontuari.payselection.base;

import java.lang.reflect.Constructor;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;

import org.adempiere.base.IPaymentExporterFactory;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.PO;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.PaymentExport;

/**
 * Dynamic payment exporter factory
 */
public abstract class FTUPaymentExporterFactory implements IPaymentExporterFactory {

	private final static CLogger log = CLogger.getCLogger(FTUPaymentExporterFactory.class);
	private List<Class<? extends PaymentExport>> cachePaymentExporters = new ArrayList<Class<? extends PaymentExport>>();
	
	/**
	 * For initialize class. Register the models to build
	 * 
	 * <pre>
	 * protected void initialize() {
	 * 	registerTableModel(MTableExample.Table_Name, MTableExample.class);
	 * }
	 * </pre>
	 */
	protected abstract void initialize();
	
	/**
	 * Register the models of plugin
	 * 
	 * @param tableName  Table name
	 * @param tableModel Model of the table
	 */
	protected void registerPaymentExporter(Class<? extends PaymentExport> paymentexporterClass) {
		cachePaymentExporters.add(paymentexporterClass);
		log.info(String.format("FTUPaymentExporter registered -> %s", paymentexporterClass.getName()));
	}

	/**
	 * Default constructor
	 */
	public FTUPaymentExporterFactory() {
		initialize();
	}
	
	public PaymentExport newPaymentExporterInstance(String className) {
		for (int i = 0; i < cachePaymentExporters.size(); i++) {
			if (className.equals(cachePaymentExporters.get(i).getName())) {
				try {
					PaymentExport FTUPaymentExporter = cachePaymentExporters.get(i).newInstance();
					log.info(String.format("FTUPaymentExporter created -> %s", className));
					return FTUPaymentExporter;
				} catch (Exception e) {
					log.severe(String.format("Class %s can not be instantiated, Exception: %s", className, e));
					throw new AdempiereException(e);
				}
			}
		}
		return null;
	}
	
}
