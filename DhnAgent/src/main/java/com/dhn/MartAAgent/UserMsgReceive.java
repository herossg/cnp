package com.dhn.MartAAgent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;


@Repository
public class UserMsgReceive {
	
	@Autowired
	@Qualifier("dhnds")
    DataSource dhnSource ;

	
	@Autowired
	@Qualifier("userds")
    DataSource userSource ;
	
	static boolean isRunning;
	
	public void ReceiveMsg(String _monthStr) {
		Logger log = LoggerFactory.getLogger(getClass());
		
		String monthStr = _monthStr; 
		
		if(!UserMsgReceive.isRunning) {
			UserMsgReceive.isRunning = true;
			int totalreceive = 0;

			String dhnTblName = "cb_grs_broadcast_" + monthStr;
			String userTblName = DbInfo.BROADCAST_TABLE + monthStr;
			
			Connection userCon = null;
			Connection dhnCon = null;
			
			try {
				userCon = userSource.getConnection();
				dhnCon = dhnSource.getConnection();
								
				String userQuery = "";
				
				if(DbInfo.DBMS.toUpperCase().equals("MYSQL") || DbInfo.DBMS.toUpperCase().equals("MARIADB")) {
					userQuery = "select (msg_id div 10) as part, group_concat(dhn_msg_id) as msg_ids from " + DbInfo.MSG_TABLE + " where msg_st = '2' and dhn_msg_id is not null group by (msg_id div 10)";
				}
				
				if(DbInfo.DBMS.toUpperCase().equals("ORACLE")) {
					userQuery = "select trunc(msg_id / 10) as part, SUBSTR(XMLAgg(XMLElement(x, ',', dhn_msg_id)).Extract('//text()'), 2) as msg_ids from " + DbInfo.MSG_TABLE + " where msg_st = '2' and dhn_msg_id is not null group by trunc(msg_id / 10)";
				}
				Statement stm = userCon.createStatement();
				
				ResultSet rs = stm.executeQuery(userQuery);
				
				while(rs.next()) {

					String dhnQuery = "select * from " + dhnTblName + " where bc_snd_st in ('3', '4', '5', '6') and msg_id in (" + rs.getString("msg_ids") + ")";
					//log.info(dhnQuery);
					Statement dhnstm = dhnCon.createStatement();
					ResultSet dhnrs = dhnstm.executeQuery(dhnQuery);
					
					String userResultInsert = "INSERT INTO " + userTblName + " (MSG_ID" + 
																				" , MSG_GB" + 
																				" , BC_MSG_ID" + 
																				" , BC_SND_ST" + 
																				" , BC_SND_PHN" + 
																				" , BC_RCV_PHN" + 
																				" , BC_RSLT_NO" + 
																				" , BC_RSLT_TEXT" + 
																				" , BC_SND_DTTM" + 
																				" , BC_RCV_DTTM) " + 
																				" VALUES " + 
																				" ( ?" + 
																				" , ?" + 
																				" , ?" + 
																				" , ?" + 
																				" , ?" + 
																				" , ?" + 
																				" , ?" + 
																				" , ?" + 
																				" , ?" + 
																				" , ?" + 
																				" )";
					
					String userResultUpdate = "update " + userTblName + " set BC_RSLT_NO = ? , BC_RSLT_TEXT = ? , BC_RCV_DTTM = ? where BC_MSG_ID = ?";
					
					while(dhnrs.next()) {
						try {
							//log.info(" DHN : " + dhnrs.getString(1));
							PreparedStatement userIns = userCon.prepareStatement(userResultInsert);
							userIns.setString(1, dhnrs.getString(1));
							userIns.setString(2, dhnrs.getString(2));
							userIns.setString(3, dhnrs.getString(3));
							userIns.setString(4, dhnrs.getString(4));
							userIns.setString(5, dhnrs.getString(5));
							userIns.setString(6, dhnrs.getString(6));
							userIns.setString(7, dhnrs.getString(7));
							userIns.setString(8, dhnrs.getString(8));
							userIns.setString(9, dhnrs.getString(9));
							userIns.setString(10, dhnrs.getString(10));
							userIns.executeUpdate();
							userIns.close();
						} catch(SQLException ex) {
							//log.info(ex.toString());
							PreparedStatement userUpdate = userCon.prepareStatement(userResultUpdate);
							userUpdate.setString(1, dhnrs.getString("BC_RSLT_NO"));
							userUpdate.setString(2, dhnrs.getString("BC_RSLT_TEXT"));
							userUpdate.setString(3, dhnrs.getString("BC_RCV_DTTM"));
							userUpdate.setString(4, dhnrs.getString("BC_MSG_ID"));
							userUpdate.executeUpdate();
							userUpdate.close();
						}
						
						String dhnUDQuery = "update " + dhnTblName + " set bc_snd_st = bc_snd_st + 2 where bc_msg_id = '" + dhnrs.getString("BC_MSG_ID") + "'";
						//log.info(dhnUDQuery);
						Statement dhnUpdate = dhnCon.createStatement();
						dhnUpdate.executeUpdate(dhnUDQuery);
						dhnUpdate.close();
						totalreceive++;
					}
					
					dhnrs.close();
					dhnstm.close();
					
				}
				
				rs.close();
				stm.close();
				
				dhnCon.close();
				userCon.close();
				
			} catch (SQLException e) {
				log.error(e.toString());
			}
			if(totalreceive > 0)
				log.info("" + totalreceive + " Message Received !!");
			
			try {
				if(userCon != null)
					userCon.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			try {
				if(dhnCon != null)
					dhnCon.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			UserMsgReceive.isRunning = false;
		}
	}
}
