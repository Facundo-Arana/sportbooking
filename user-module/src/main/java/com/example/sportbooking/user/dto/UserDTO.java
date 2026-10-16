package com.example.sportbooking.user.dto;

import java.sql.Timestamp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
	

	
	private String username;
	
	private String email;
	
	private String password;
	
	private String name;
	
	private Timestamp created_at;
	
	


	
	

}
