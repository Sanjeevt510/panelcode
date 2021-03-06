package com.akujas.vcs.web.rest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.akujas.vcs.RequestResponseUtil;
import com.akujas.vcs.config.ApplicationProperties;
import com.akujas.vcs.domain.Wallet;
import com.akujas.vcs.service.WalletService;
import com.akujas.vcs.web.rest.errors.BadRequestAlertException;
import com.akujas.vcs.web.rest.util.HeaderUtil;
import com.akujas.vcs.web.rest.util.PaginationUtil;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing Wallet.
 */
@RestController
@RequestMapping("/api")
@Configuration
@EnableConfigurationProperties({ApplicationProperties.class})
public class WalletResource {

    private final Logger log = LoggerFactory.getLogger(WalletResource.class);

    private static final String ENTITY_NAME = "wallet";

    private final WalletService walletService;
    
    @Value("${appdetail.cbtomerchanturl}")    
    public String cbtomerchanturl;
    
    @Value("${appdetail.cbwalletid}")    
    public String cbwalletid;
    
    public WalletResource(WalletService walletService) {
        this.walletService = walletService;
    }

    /**
     * POST  /wallets : Create a new wallet.
     *
     * @param wallet the wallet to create
     * @return the ResponseEntity with status 201 (Created) and with body the new wallet, or with status 400 (Bad Request) if the wallet has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/wallets")
    @Timed
    public ResponseEntity<Wallet> createWallet(@RequestBody Wallet wallet) throws URISyntaxException {
        log.debug("REST request to save Wallet : {}", wallet);
        if (wallet.getId() != null) {
            throw new BadRequestAlertException("A new wallet cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Wallet result = walletService.save(wallet);
        return ResponseEntity.created(new URI("/api/wallets/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /wallets : Updates an existing wallet.
     *
     * @param wallet the wallet to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated wallet,
     * or with status 400 (Bad Request) if the wallet is not valid,
     * or with status 500 (Internal Server Error) if the wallet couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/wallets")
    @Timed
    public ResponseEntity<Wallet> updateWallet(@RequestBody Wallet wallet) throws URISyntaxException {
        log.debug("REST request to update Wallet : {}", wallet);
        if (wallet.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        Wallet result = walletService.save(wallet);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, wallet.getId().toString()))
            .body(result);
    }

    /**
     * GET  /wallets : get all the wallets.
     *
     * @param pageable the pagination information
     * @return the ResponseEntity with status 200 (OK) and the list of wallets in body
     */
    @GetMapping("/wallets")
    @Timed
    public ResponseEntity<List<Wallet>> getAllWallets(Pageable pageable,@RequestParam("merchantid") String merchantid) {
        log.debug("REST request to get a page of Wallets");
        Page<Wallet> page=null;

		if (StringUtils.isNotBlank(merchantid) && !"null".equals(merchantid)) { 
			long mid=Long.parseLong(merchantid);
			page = walletService.findWalletByMerchant(pageable, mid);
		}else {
			page = walletService.findAll(pageable);
		}
        
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(page, "/api/wallets");
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }
    
    
    @GetMapping("/cb/wallets")
    @Timed
    public ResponseEntity<Wallet> getCentralBankWallet() {
    	log.debug("REST request to get cb Wallet : {}", "");
    	long id = Long.parseLong(cbwalletid);
        Optional<Wallet> wallet = walletService.findOne(id);
        return ResponseUtil.wrapOrNotFound(wallet);
    }
    
    @PostMapping ("/cb/wallets/transfer")
    @Timed
	public ResponseEntity<Wallet> centralBankToMerchantTransfer(@RequestBody Map<String, Object> body)
			throws URISyntaxException {

		// {destinationmid={id=1, name=dummy_merchant,
		// displayName=dummy_merchant, description=dummy_merchant,
		// mid=dummy_merchant, params=null, param1=null, param2=null,
		// param3=null, midSecret=ce3e1b4ddb7d18efe05354bb638065be,
		// createdBy=null, created=2018-12-07T14:45:49Z,
		// modifiedon=2018-12-09T15:01:47Z, pincode=null, address=null,
		// isenabled=true}, amount=111}

		long id = Long.parseLong(cbwalletid);
		Optional<Wallet> result2 = walletService.findOne(id);
		Wallet result = result2.get();

		String clientid = "";
		String code="";
		String messgae="";
		try {
			String vcsamount = body.get("amount").toString();
			Map<String, Object> data = new HashMap<>();
			data.put("description", "from cb to wallet");
			data.put("points", vcsamount);
			data.put("order_id", UUID.randomUUID().toString());

			String datajson = new ObjectMapper().writeValueAsString(data);

			
			//clientid="dummy_merchant";
		    //String clientsecret="dummyone";
			
			clientid = ((Map<String, Object>) body.get("destinationmid")).get("mid").toString();
			String clientsecret = ((Map<String, Object>) body.get("destinationmid")).get("midSecret").toString();
			
			String time = "" + (new Date().getTime());
			String merchantauth = clientid + ":" + clientsecret + ":" + time;
			byte[] encodeBase64 = Base64.encodeBase64(merchantauth.getBytes());
			String merchant_auth = "Basic " + new String(encodeBase64);

			Map<String, Object> headers = new HashMap<>();
			headers.put("content-type", "application/json");
			headers.put("cache-control", "no-cache");
			headers.put("merchant_auth", merchant_auth);

			String httpPostRequest = "" + RequestResponseUtil.getInstance()
					.HTTPPostRequest(cbtomerchanturl, datajson, headers);

			Map<String, String> map = new HashMap<String, String>();
			ObjectMapper mapper = new ObjectMapper();
			map = mapper.readValue(httpPostRequest, new TypeReference<HashMap<String, String>>() {
			});

			code = map.get("code");
			messgae=map.get("message");
			if ("2001".equals(code)) {
				return ResponseEntity.created(new URI("/cb/wallets/" + result.getId()))
						.headers(HeaderUtil.createCBtoMerchantAmountaddAlert(clientid)).body(result);

			}

		} catch (Exception e) {
			log.error("",e);
			return ResponseEntity.created(new URI("/cb/wallets/" + result.getId()))
					.headers(HeaderUtil.createFailureAlert(clientid,code,"something went wrong please try again")).body(result);
		}

		return ResponseEntity.created(new URI("/cb/wallets/" + result.getId()))
				.headers(HeaderUtil.createFailureAlert(clientid,code,messgae)).body(result);
	}    
    
    
    static String extractPostRequestBody(HttpServletRequest request) {
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            Scanner s = null;
            try {
                s = new Scanner(request.getInputStream(), "UTF-8").useDelimiter("\\A");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return s.hasNext() ? s.next() : "";
        }
        return "";
    }
    
    

    /**
     * GET  /wallets/:id : get the "id" wallet.
     *
     * @param id the id of the wallet to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the wallet, or with status 404 (Not Found)
     */
    @GetMapping("/wallets/{id}")
    @Timed
    public ResponseEntity<Wallet> getWallet(@PathVariable Long id) {
        log.debug("REST request to get Wallet : {}", id);
        Optional<Wallet> wallet = walletService.findOne(id);
        return ResponseUtil.wrapOrNotFound(wallet);
    }

    /**
     * DELETE  /wallets/:id : delete the "id" wallet.
     *
     * @param id the id of the wallet to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/wallets/{id}")
    @Timed
    public ResponseEntity<Void> deleteWallet(@PathVariable Long id) {
        log.debug("REST request to delete Wallet : {}", id);
        walletService.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
