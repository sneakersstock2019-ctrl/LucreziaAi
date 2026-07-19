//package it.sd.lucrezia.ai.controller;
//
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestMethod;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
//import it.sd.lucrezia.ai.bean.Utente;
//import it.sd.lucrezia.ai.dao.TelefonataDao;
//import it.sd.lucrezia.ai.dao.TicketDao;
//import it.sd.lucrezia.ai.dao.UtenteDao;
//import it.sd.lucrezia.ai.service.elevenlabs.ElevenLabsService;
//import it.sd.lucrezia.ai.util.PhoneUtils;
//import lombok.RequiredArgsConstructor;
//
//@RestController
//@RequestMapping("/voice")
//@RequiredArgsConstructor
//public class VoiceController {
//
//	private final UtenteDao utenteDao;
//	private final TicketDao ticketDao;
//    private final PhoneUtils phoneUtils;
//    private final ElevenLabsService elevenLabsService;
//    private final TelefonataDao telefonataDao;
//
//    @RequestMapping(
//            value = "/incoming",
//            method = {RequestMethod.GET, RequestMethod.POST},
//            produces = "application/xml"
//    )
//    public String incomingCall(@RequestParam(value = "From", required = false) String from, @RequestParam(value = "To", required = false) String to, @RequestParam(value = "CallSid", required = false) String callSid) {
//
//    	try {
//    		String phone = phoneUtils.normalizePhone(from);
//
//    		System.out.println("############################");
//    		System.out.println("TWILIO INCOMING CALL");
//    		System.out.println("CALL SID = " + callSid);
//    		System.out.println("FROM = " + from);
//    		System.out.println("PHONE = " + phone);
//    		System.out.println("############################");
//
//    		Utente utente = utenteDao.findCondominoByTelefono(phone);
//
//    		if (utente == null) {
//    			return null;
//    		}
//
//    		Long idTelefonata = telefonataDao.insertTelefonata(
//    				callSid,
//    				phone,
//    				utente.getId(),
//    				utente.getIdCondominio()
//    				);
//
//    		System.out.println("ROUTING VOICE ENGINE = ELEVENLABS");
//    		int ticketAperti = ticketDao.findOpenTicketsByUtente(utente.getId()).size();
//
//    		return elevenLabsService.registerInboundCall(
//    				from,
//    				to,
//    				callSid,
//    				idTelefonata,
//    				utente,
//    				ticketAperti
//    				);
//
//    	} catch (Exception e) {
//    		e.printStackTrace();
//    		return null;
//    	}
//    }
//    
//    @PostMapping("/status")
//    public ResponseEntity<String> callStatus(@RequestParam(value = "CallSid", required = false) String callSid,
//                                             @RequestParam(value = "CallStatus", required = false) String callStatus,
//                                             @RequestParam(value = "CallDuration", required = false) String callDuration) {
//
//        System.out.println("############################");
//        System.out.println("TWILIO CALL STATUS");
//        System.out.println("CALL SID = " + callSid);
//        System.out.println("STATUS = " + callStatus);
//        System.out.println("DURATION = " + callDuration);
//        System.out.println("############################");
//
//        return ResponseEntity.ok("OK");
//    }
//    
//}