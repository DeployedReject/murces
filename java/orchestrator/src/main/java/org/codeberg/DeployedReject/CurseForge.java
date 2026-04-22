package org.codeberg.DeployedReject;

import java.util.HashMap;

public class CurseForge {

  public String type;
  public String subtype;
  public int id = 432;
  public String modName;
  public String version;
  String baseURL = "https://api.curseforge.com";

  public void curseForgeHandler() {
    if (subtype.equals("search"))
      search(true);
    else if (subtype.equals("download")) {
      // To Do
    } else if (subtype.equals("Home Page")) {
      // TO Do
    }

  }

  public void search(boolean mode){
    //Mod Loader Translator
    HashMap<String,Integer> mLT = new HashMap<>();
    mLT.put("forge", 1);
    mLT.put("fabric",4);
    mLT.put("liteLoader",3);
    mLT.put("cauldron",2);
    mLT.put("quilt",5);
    mLT.put("neoForge",6);


    if(mode){
      url = baseURL+"/v1/categories?gameID="+id



    }
  }
}
