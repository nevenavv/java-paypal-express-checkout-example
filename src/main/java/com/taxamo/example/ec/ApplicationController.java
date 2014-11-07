package com.taxamo.example.ec;

import com.taxamo.client.api.TaxamoApi;
import com.taxamo.client.common.ApiException;
import com.taxamo.client.model.ConfirmTransactionIn;
import com.taxamo.client.model.GetTransactionOut;
import org.springframework.http.*;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.apache.commons.codec.binary.Base64;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.*;

@Controller
public class ApplicationController {

    final String ppUser = System.getenv("PP_USER");
    final String ppPass = System.getenv("PP_PASS");
    final String ppSign = System.getenv("PP_SIGN");

    final String privateToken = System.getenv("PRIVATE_TOKEN"); //SamplePrivateTestKey1
    final String publicToken = System.getenv("PUBLIC_TOKEN"); //SamplePublicTestKey1

    private Properties properties;

    private TaxamoApi taxamoApi;

    @RequestMapping(value = "/")
    public String index() {
        return "index";
    }

    @RequestMapping(value = "/cancel")
    public String cancel() {
        return "cancel";
    }

    @RequestMapping(value = "/confirm")
    public String confirm(@RequestParam("transactionKey") String transactionKey, Model model) {
        try {
            taxamoApi.confirmTransaction(transactionKey, new ConfirmTransactionIn());
        }
        catch (ApiException ae) {
            model.addAttribute("error", "ERROR result: " + ae.getMessage());
            return "error";
        }
        model.addAttribute("transactionKey", transactionKey);

        return "confirm";
    }

    @RequestMapping(value = "/success-checkout")
    public String success(@RequestParam("PayerID") String payer, @RequestParam("token") String token, @RequestParam("taxamo_transaction_key") String transactionKey, Model model) {

        try {
            GetTransactionOut transaction = taxamoApi.getTransaction(transactionKey);
            model.addAttribute("total", transaction.getTransaction().getTotalAmount());
        } catch (ApiException ae) {
            model.addAttribute("error", "Error, status returned: " + ae.getMessage());
            return "error";
        }

        model.addAttribute("transactionKey", transactionKey);

        return "success";
    }

    @RequestMapping("/express-checkout")
    public String expressCheckout(Model model) {

        RestTemplate template = new RestTemplate();

        MultiValueMap<String, String> map = new LinkedMultiValueMap<String, String>();
        map.add("USER", ppUser);
        map.add("PWD", ppPass);
        map.add("SIGNATURE", ppSign);
        map.add("VERSION", "117");
        map.add("METHOD", "SetExpressCheckout");
        map.add("returnUrl", properties.get(PropertiesConstants.STORE).toString() + properties.get(PropertiesConstants.SUCCESS_LINK).toString());
        map.add("cancelUrl", properties.get(PropertiesConstants.STORE).toString() + properties.get(PropertiesConstants.CANCEL_LINK).toString());

        //shopping item(s)
        map.add("PAYMENTREQUEST_0_AMT", "20.00"); // total amount
        map.add("PAYMENTREQUEST_0_PAYMENTACTION", "Sale");
        map.add("PAYMENTREQUEST_0_CURRENCYCODE", "EUR");

        map.add("L_PAYMENTREQUEST_0_NAME0", "ProdName");
        map.add("L_PAYMENTREQUEST_0_DESC0", "ProdName desc");
        map.add("L_PAYMENTREQUEST_0_AMT0", "20.00");
        map.add("L_PAYMENTREQUEST_0_QTY0", "1");
        map.add("L_PAYMENTREQUEST_0_ITEMCATEGORY0", "Digital");

        List<HttpMessageConverter<?>> messageConverters = new ArrayList<HttpMessageConverter<?>>();
        messageConverters.add(new FormHttpMessageConverter());
        messageConverters.add(new StringHttpMessageConverter());
        template.setMessageConverters(messageConverters);

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<MultiValueMap<String, String>>(map, requestHeaders);
        ResponseEntity<String> res = template.exchange(URI.create(properties.get(PropertiesConstants.PAYPAL_NVP).toString()), HttpMethod.POST , request, String.class);

        Map<String, List<String>> params = parseQueryParams(res.getBody());

        String ack = params.get("ACK").get(0);
        if (!ack.equals("Success")) {
            model.addAttribute("error", params.get("L_LONGMESSAGE0").get(0));
            return "error";
        }
        else {
            String token = params.get("TOKEN").get(0);
            return "redirect:"+ properties.get(PropertiesConstants.TAXAMO)+"/checkout/index.html?"+
                    "token="+token+
                    "&public_token="+publicToken+
                    "&billing_country_code="+"IE"+
                    "&cancel_url="+ new String(Base64.encodeBase64(new String(properties.get(PropertiesConstants.STORE).toString() + properties.get(PropertiesConstants.CANCEL_LINK).toString()).getBytes()))+
                    "&return_url=" + new String(Base64.encodeBase64(new String(properties.get(PropertiesConstants.STORE).toString() + properties.get(PropertiesConstants.SUCCESS_LINK).toString()).getBytes())) +
                    "#/paypal_express_checkout";
        }
    }

    @PostConstruct
    public void init() {
        properties = new Properties();
        try {
            properties.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("config.properties"));
        }
        catch (IOException e) {
            System.err.println(e.getStackTrace());
        }

        taxamoApi = new TaxamoApi(privateToken);
        taxamoApi.setBasePath(properties.get(PropertiesConstants.TAXAMO).toString());
    }

    private Map<String, List<String>> parseQueryParams(String qparams) {
        try {
            Map<String, List<String>> params = new HashMap<String, List<String>>();
            for (String param : qparams.split("&")) {
                String[] pair = param.split("=");
                String key = URLDecoder.decode(pair[0], "UTF-8");
                String value = "";
                if (pair.length > 1) {
                    value = URLDecoder.decode(pair[1], "UTF-8");
                }

                List<String> values = params.get(key);
                if (values == null) {
                    values = new ArrayList<String>();
                    params.put(key, values);
                }
                values.add(value);
            }

            return params;
        } catch (UnsupportedEncodingException ex) {
            throw new AssertionError(ex);
        }
    }

}