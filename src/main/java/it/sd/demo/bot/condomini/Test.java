package it.sd.demo.bot.condomini;

import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

public class Test {

	public static void main(String[] args) {
		invioMessaggio("393492123304", "Ciao bellissimo");
	}
	
	private static void invioMessaggio(String to, String testoMessaggio) {
		RestTemplate restTemplate = new RestTemplate();
		String token = "EAAUPRCmZC4eMBRsYqcoqy5fJz9ZAWOOzob1ZAXojGps0on9g0cavE0JWUJWgvYuZB34CtZCZAdtPhZAjai5LRJucTZCGlhUTkQu0M0BlsC8h7ZCxHZAUzsnbgwFOuZCrcvqJd2fZCFTfTmCjVWziWZAeetW3ctGTOgqFiIcGgmCMeixgS6zF545iDEg0BQEMP7N7r5AZDZD";
		String urlApiMetaMessages = "https://graph.facebook.com/v25.0/1095264977009887/messages";
		
		Map<String, Object> text = null;
    	Map<String, Object> payload = null;
    	HttpHeaders httpHeaders = null;
    	HttpEntity<Map<String, Object>> httpEntity = null;
    	
        try {
            text = Map.of("body", testoMessaggio);
            
            payload = Map.of(
                    "messaging_product", "whatsapp",
                    "to", to,
                    "type", "text",
                    "text", text
            );

            httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            httpHeaders.setBearerAuth(token);

            httpEntity = new HttpEntity<>(payload, httpHeaders);
            
            System.out.println("Invoco Api Meta Messages (POST): " + urlApiMetaMessages);
            System.out.println("Headers: " + httpHeaders);
            System.out.println("Payload: " + payload);
            restTemplate.postForEntity(urlApiMetaMessages, httpEntity, String.class);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
