package com.ewolff.microservice.shipping.poller;

import java.util.Date;

import org.apache.http.client.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.ewolff.microservice.shipping.Shipment;
import com.ewolff.microservice.shipping.ShipmentRepository;
import com.ewolff.microservice.shipping.ShipmentService;
import com.rometools.rome.feed.atom.Entry;
import com.rometools.rome.feed.atom.Feed;

@Component
public class ShippingPoller {

	private final Logger log = LoggerFactory.getLogger(ShippingPoller.class);

	private String url = "";

	private RestTemplate restTemplate = new RestTemplate();

	private Date lastModified = null;

	private ShipmentRepository shippingRepository;

	private ShipmentService shipmentService;

	private boolean pollingActivated = true;

	@Autowired
	public ShippingPoller(@Value("${order.url}") String url, @Value("${poller.actived:true}") boolean pollingActivated,
			ShipmentRepository shippingRepository, ShipmentService shipmentService) {
		super();
		this.url = url;
		this.shippingRepository = shippingRepository;
		this.shipmentService = shipmentService;
		this.pollingActivated = pollingActivated;
	}

	@Scheduled(fixedDelay = 30000)
	public void poll() {
		if (pollingActivated) {
			pollInternal();
		}
	}

	public void pollInternal() {
		HttpHeaders requestHeaders = new HttpHeaders();
		if (lastModified != null) {
			requestHeaders.set("If-Modified-Since", DateUtils.formatDate(lastModified));
		}
		HttpEntity<?> requestEntity = new HttpEntity(requestHeaders);
		ResponseEntity<Feed> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, Feed.class);

		if (response.getStatusCode() != HttpStatus.NOT_MODIFIED) {
			log.trace("data has been modified");
			Feed feed = response.getBody();
			for (Entry entry : feed.getEntries()) {
				if ((lastModified == null) || (entry.getUpdated().after(lastModified))) {
					Shipment shipping = restTemplate
							.getForEntity(entry.getContents().get(0).getSrc(), Shipment.class).getBody();
					log.trace("saving shipping {}", shipping.getId());
					shipmentService.ship(shipping);
				}
			}
			if (response.getHeaders().getFirst("Last-Modified") != null) {
				lastModified = DateUtils.parseDate(response.getHeaders().getFirst("Last-Modified"));
				log.trace("Last-Modified header {}", lastModified);
			}
		} else {
			log.trace("no new data");
		}
	}

}
