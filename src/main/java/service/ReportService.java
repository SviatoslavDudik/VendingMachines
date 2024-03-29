package service;

import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.ListIterator;

import javax.jws.WebService;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.hibernate.Session;
import org.hibernate.Transaction;

import machines.DailyReport;
import machines.Error;
import machines.Product;
import machines.ProductReport;
import machines.VendingMachine;
import util.HibernateUtil;
import org.json.*;

@WebService
@Path("/")
public class ReportService {
	
	public ReportService() {

	}

	@POST
	@Path("/report")
	@Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	public Response addReport(DailyReport report) {
		
		// link errors with the report
		if (report.getErrors() != null) {
			for (Error error : report.getErrors()) {
				error.setReport(report);
			}
		}

		Session session = HibernateUtil.getSessionFactory().getCurrentSession();
		Transaction transaction = session.beginTransaction();

		if (productsExist(report.getProducts()) == false) {
			transaction.rollback();
			return Response.notModified().build();
		}
		
		// link machine with the report
		if (report.getMachine() == null) {
			transaction.rollback();
			return Response.notModified("Machine not provided").build();
		}
		VendingMachine machine = session.bySimpleNaturalId(VendingMachine.class).load(report.getMachine().getSerialNb());
		if (machine == null) {
			transaction.rollback();
			return Response.notModified("Machine doesn't exist").build();
		}
		machine.setLastReport(report);
		report.setMachine(machine);
		session.persist(machine);
		
		
		String city = machine.getInstallationAddress().getCity();
		
		
		// persist the report to get an id assigned to it
		session.persist(report);
		transaction.commit();

		String link = "http://api.openweathermap.org/data/2.5/weather?q=";
		link = link + city + "&appid=e7f3095eddadad18036fd4803773eb0d&units=metric";
		URL url;
		
		try {
			url = new URL(link);
			URLConnection con = (URLConnection) url.openConnection();
			JSONObject json_object = new JSONObject(new JSONTokener(con.getInputStream()));
			json_object = json_object.getJSONObject("main");
			float temp = json_object.getFloat("temp");
			report.setTemperatureExt(temp);
		} 
		catch (Exception e) {
			report.setTemperatureExt(-274);
			e.printStackTrace();
		}		
		
		session = HibernateUtil.getSessionFactory().getCurrentSession();
		transaction = session.beginTransaction();
		session.update(report);
		
		// set link products with the report (using report's id)
		for (ProductReport pr : report.getProducts()) {
			// using report's id
			pr.getId().setReportId(report.getId());
			session.save(pr);
		}
		transaction.commit();
		
		return Response.ok().build();
	}
	
	private boolean productsExist(List<ProductReport> products) {
		Session session = HibernateUtil.getSessionFactory().getCurrentSession();
		if (products == null) {
			return true;
		}
		ListIterator<ProductReport> prIterator = products.listIterator();
		while (prIterator.hasNext()) {
			ProductReport pr = prIterator.next();
			if (session.find(Product.class, pr.getId().getProductId()) == null) {
				return false;
			}
		}
		return true;
	}

}
