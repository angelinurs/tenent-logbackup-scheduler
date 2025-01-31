package com.ez.ncpsdktomcat.controller;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ez.ncpsdktomcat.config.ObjectStorageProps;
import com.ez.ncpsdktomcat.deprecated.FileEncrypterDecrypter;
import com.ez.ncpsdktomcat.deprecated.GzipComponent;
import com.ez.ncpsdktomcat.service.BackupComponent;
import com.ez.ncpsdktomcat.service.DBService;
import com.ez.ncpsdktomcat.service.KeyBinder;
import com.ez.ncpsdktomcat.service.ScriptComponent;
import com.ez.ncpsdktomcat.vo.LogMaterialVO;
import com.ez.ncpsdktomcat.vo.TenencySchemaVO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/db")
public class DBController {
		
	@Autowired
	@Qualifier( value = "userJdbcTemplate" )
	private JdbcTemplate userJdbcTemplate;
	
	private DBService dbService;
	
	@Autowired
	private BackupComponent backupComponent;
	
	private FileEncrypterDecrypter fileEncrypterDecrypter;
	
	@Autowired
	private GzipComponent gzipComponent;
		
	@Autowired
	@Qualifier( value = "portalJdbcTemplate")
	private JdbcTemplate portalJdbcTemplate;
	
	// encrypt key
	private KeyBinder keyBinder;

	@Autowired
	private ScriptComponent scriptComponent;
	
	@GetMapping("/list2")
	public String getSchemaList2() {
		
		dbService = new DBService( userJdbcTemplate );
		
		List<TenencySchemaVO> results =  dbService.getSchemaList();
		
		StringBuilder sb = new StringBuilder();
		for( TenencySchemaVO vo : results ) {
			sb.append( vo.toString() ).append( "<br />" );
			
		}
		
		log.error( "result index 0 : {}", results.get(0) );
		
		return sb.toString();
		
	}
	
//	@GetMapping("/dump")
//	public String doDump() {
//		String key = "naru";
//		String schema = "psm_sc_svc171";	
//		String date = "$(date +%Y)-$(date +%m)-$(date +%d)";
//		String time = "$$(date +%H):$(date +%M):$(date +%S)";
//		
//		String command = psqlClientComponent.doDumpSchemas( schema, date, time, "user", key );
//		
//		return command; 
//		
//	}
	
	@GetMapping("/dumpall_schema")
	public String dumpall_schema() {
		
		List<TenencySchemaVO> users = backupComponent.dumpallDBSchema( "user" );
		List<TenencySchemaVO> portals = backupComponent.dumpallDBSchema( "portal" );
		
		backupComponent.exportSchemasToObjectStorage( users );
		backupComponent.exportSchemasToObjectStorage( portals );
		
		return "done";
	}
	
	@GetMapping("/dumpall_logs")
	public String dumpall_logs() {
		String key = "naru";
		
		List<LogMaterialVO> logMaterialVOs = backupComponent.dumpallLogs();
		backupComponent.exportLogsToObjectStorage( logMaterialVOs );
		
		return "done";
	}
	
	@GetMapping( "/compress" )
	public String doCompress() {
		String dirPath = "/home/naru/temp";
		String schema = "psm_sc_svc171";	
		
		String filename = String.format( "%s/%s.tar", dirPath, schema );
		String gzipFilename = String.format( "%s/%s.tar.gz", dirPath, schema );
		gzipComponent.compressGzipFile( filename, gzipFilename );
		
		return gzipFilename;
	}
	
	@GetMapping( "/ecrypt" ) 
	public String doEncrypt() {
		
		String filename = "/home/naru/temp/baz.enc";
		String originalContent = "foobar";
		String decryptedContent = null;
		
	    SecretKey secretKey = null;
	    
	    try {
//			secretKey = KeyGenerator.getInstance("AES").generateKey();
//			fileEncrypterDecrypter = new FileEncrypterDecrypter(secretKey, "AES/CBC/PKCS5Padding");
			fileEncrypterDecrypter = new FileEncrypterDecrypter( "AES/CBC/PKCS5Padding");
			fileEncrypterDecrypter.encrypt(originalContent, filename );
			
			decryptedContent = fileEncrypterDecrypter.decrypt( filename );
//	    	assertThat(decryptedContent, is(originalContent));
			
//			new File("baz.enc").delete(); // cleanup
			
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IOException | InvalidAlgorithmParameterException e) {
			e.printStackTrace();
		}     
	    
	    StringBuilder sb = new StringBuilder();
	    sb.append( "originalContent : "  ).append( originalContent ).append( "\n" )
	      .append( "decryptedContent : " ).append( decryptedContent ).append( "\n" )
	      .append( "same ? " ).append( originalContent.equals( decryptedContent ) ? "same": "not same" ).append( "\n" );
	    
	    return sb.toString();
	}
	
	@GetMapping("/profile")
	public void getProfileName() {
		JdbcTemplate jdbcTemplate = null;
		
		String kind = "user";
		
		switch( kind ) {
		case "user":
			jdbcTemplate = userJdbcTemplate;			
			break;
			
		case "portal":
			jdbcTemplate = portalJdbcTemplate;			
			break;
		}
		
//		if( jdbcTemplate == null ) {
//			return null;
//		}
		
		// inquiry list of schema
		List<TenencySchemaVO> results = ( new DBService( jdbcTemplate )).getSchemaList();
		
		log.info("============ Start Backup ================");
		
		// dump, compress, encrypt all schema 
		for( TenencySchemaVO vo : results ) {
			if( vo.getProfile().equalsIgnoreCase("svc215") ) {
				keyBinder = new KeyBinder( portalJdbcTemplate );
				String key = keyBinder.getKey( vo.getProfile() );
				
				log.info( "key binder test : {}", String.format("vo.getProfile() : %s %nkey : %s", vo.getProfile(), key));
				log.info( "key binder to_string : {}", vo.toString() );
				
				vo.setKey(key);
				
				String command = scriptComponent.doDumpSchemas( vo, kind );
				
				log.info( "Command : {}", command );
				
			}
		}
		
	}
	
}
