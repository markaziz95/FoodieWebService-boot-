package com.rutgers.cs417.project2;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.httpclient.HttpStatus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Controller
public class FoodieWebServiceController {
	@RequestMapping("/")
	public @ResponseBody String HomePage() {
	    return "<h1>Welcome to Foodie Web Services</h1><p>Our web service takes a HTTP request with an address parameter and respond with a list of popular nearby restaurants.</p><br /><hr /><h1>How to use our service:</h1><p>In the address bar, enter http://localhost:8080/restaurants/?address=&lt;ADDRESS&gt;</p><p>Make sure to have a valid address containing:</p><ol>  <li>Street </li>  <li>City/Town/Village </li>  <li>Zip Code</li></ol>";
	}
	
	//@RequestMapping(value ="restaurants", method = RequestMethod.GET)   //this works too
	@RequestMapping("/restaurants")
	public @ResponseBody JsonNode foodieWebService(@RequestParam("address") String address) throws IOException, JSONException {
		//used to parse json later
		ObjectMapper mapper = new ObjectMapper();
		System.out.println(address);

		if(address.equals("")) {
			String emptyAddress = "{\"Bad Request\": \"Address parameter is empty\"}";
			return mapper.readTree(emptyAddress);
		}
		
		//create RestTemplate to make calls to REST APIs
		RestTemplate restTemplate = new RestTemplate();		
		String geocodUrl = "https://api.geocod.io/v1.3/geocode?api_key=e69dd65d59f64d56bb99e5fea55f5b1d999a696&q="+address;
		
		ResponseEntity<String> geocodResponse = null;
		try {
			geocodResponse = restTemplate.exchange(geocodUrl, HttpMethod.GET, null, String.class); //this null because we are not putting anything in headers
		}
		catch(HttpStatusCodeException e) {
			System.out.println("Geocod.io Status Code Value: " + e.getStatusCode().value());
			String err =  "{\"" + e.getStatusCode().value()+ " Internal Server Error\": \"Your request couldn’t be processed. Try a valid address\"}";
			return mapper.readTree(err);
		}
		
		System.out.println("Geocod.io Status Code Value: " + geocodResponse.getStatusCodeValue());
		
		// parsing geocodio response to get lat and lng using org.json (simple JSON library)
		JSONObject geocodJson = new JSONObject(geocodResponse.getBody());
		JSONArray resultsArray = geocodJson.getJSONArray("results");
		JSONObject resultsObject = resultsArray.getJSONObject(0);
		double lat = resultsObject.getJSONObject("location").getDouble("lat");
		double lng = resultsObject.getJSONObject("location").getDouble("lng");
		
		//now let's do Zomato API HTTP request
		String zomatoUrl = "https://developers.zomato.com/api/v2.1/geocode?lat="+lat+"&lon="+lng;
		
		//Zomato API takes the user_key parameter in the headers
		HttpHeaders geocodHeader = new HttpHeaders();
		geocodHeader.add("user_key", "188eda180987998d1dd37a7b93fee08a");
		HttpEntity<String> entity = new HttpEntity<String>(geocodHeader);
		
		ResponseEntity<String> zomatoResponse = null;
		try {
			zomatoResponse = restTemplate.exchange(zomatoUrl, HttpMethod.GET, entity, String.class);
		}catch(HttpClientErrorException  e) {
			System.out.println("Zomato Status Code Value: " + e.getStatusCode().value());
			String err =  "{\"" + e.getStatusCode().value()+ " Internal Server Error\": \"Your request couldn’t be processed. Try a valid address\"}";
			return mapper.readTree(err);
		}
		catch(HttpStatusCodeException e) {
			System.out.println("Zomato Status Code Value: " + e.getStatusCode().value());
			String err =  "{\"" + e.getStatusCode().value()+ " Internal Server Error\": \"Your request couldn’t be processed. Try a valid address\"}";
			return mapper.readTree(err);
		}

		
		System.out.println("Zomato Status Code Value: " + zomatoResponse.getStatusCodeValue());
		
		//parsing zomato response to get list of nearby restaurants	using Jackson  
		JsonNode root = mapper.readTree(zomatoResponse.getBody());
		JsonNode list = root.get("nearby_restaurants");
		
		//create json as a string first
		String jsonstring = "{\"restaurants\": [";
		for (int i = 0; i< list.size(); i++) {
			jsonstring += "{\"name\": " +list.get(i).get("restaurant").get("name")+", "+
					"\"address\": "+list.get(i).get("restaurant").get("location").get("address")+", "+
			"\"cuisines\": "+list.get(i).get("restaurant").get("cuisines")+", "+
			"\"rating\": "+list.get(i).get("restaurant").get("user_rating").get("aggregate_rating")+"}, ";
		}
		jsonstring = jsonstring.substring(0, jsonstring.length()-2); //just to remove these last ", " characters from the end of JSON object
		jsonstring+="]}";
		
		System.out.println(jsonstring);
		
		//convert JSON string into a json object and return to user
		JsonNode output = mapper.readTree(jsonstring);
		return output;
	}
}
