package model;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;

import org.apache.commons.lang3.ObjectUtils.Null;
import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import esayhelper.DBHelper;
import esayhelper.JSONHelper;
import esayhelper.formHelper;
import esayhelper.jGrapeFW_Message;
import interrupt.interrupt;
import rpc.execRequest;

public class ContentGroupModel {
	private static formHelper _form;
	private static DBHelper dbcontent;
	static {
		dbcontent = new DBHelper("mongodb", "type");
		_form = dbcontent.getChecker();
	}

	// public db getDB() {
	// return dbcontent;
	// }

	public ContentGroupModel() {
		_form.putRule("name", formHelper.formdef.notNull);
		_form.putRule("type", formHelper.formdef.notNull);
	}

	public JSONObject find_contentnamebyName(String name, String type) {
		JSONObject object = dbcontent.eq("name", name).eq("type", type).find();
		return object;
	}

	// 根据类型查询栏目，指定数量
	public JSONArray findByType(String type, int no) {
		JSONArray array = dbcontent.eq("type", type).limit(no).select();
		return join(array);
	}

	// 获取模版id，合并到jsonarray
	@SuppressWarnings("unchecked")
	private JSONArray join(JSONArray array) {
		JSONArray arrays = new JSONArray();
		for (int i = 0, len = array.size(); i < len; i++) {
			JSONObject object = (JSONObject) array.get(i);
			if (object.get("tempid").toString().equals("0")) {
				object.put("tempname", null);
			}else{
				String temp = execRequest
						._run("GrapeTemplate/TemplateContext/TempFindByTid/s:" + object.get("tempid").toString(), null)
						.toString();
				object.put("tempname", temp);
			}
			arrays.add(object);
		}
		return arrays;
	}

	/**
	 * 
	 * @param groupinfo
	 * @return 1 内容组名称超过指定长度 2必填项没有填 3 表示该内容组已存在
	 * 
	 */
	public String AddGroup(JSONObject groupinfo) {
		if (!_form.checkRuleEx(groupinfo)) {
			return resultMessage(2, "");
		}
		String name = groupinfo.get("name").toString(); // 内容组名称长度最长不能超过20个字数
		if (!check_name(name)) {
			return resultMessage(1, "");
		}
		String type = groupinfo.get("type").toString();
		if (find_contentnamebyName(name, type) != null) {
			return resultMessage(3, "");
		}
		String info = dbcontent.data(groupinfo).insertOnce().toString();
		return find(info).toString();
	}

	public int UpdateGroup(String ogid, JSONObject groupinfo) {
		if (find_contentnamebyName(groupinfo.get("name").toString(), groupinfo.get("type").toString()) != null) {
			return 3;
		}
		String name = groupinfo.get("name").toString(); // 内容组名称长度最长不能超过20个字数
		if (!check_name(name)) {
			return 1;
		}
		return dbcontent.eq("_id", new ObjectId(ogid)).data(groupinfo).update() != null ? 0 : 99;
	}

	public int DeleteGroup(String ogid) {
		return dbcontent.eq("_id", new ObjectId(ogid)).delete() != null ? 0 : 99;
	}

	public JSONObject find(String ogid) {
		return dbcontent.eq("_id", new ObjectId(ogid)).find();
	}

	@SuppressWarnings("unchecked")
	public JSONArray select(String contentInfo) {
		JSONObject object = JSONHelper.string2json(contentInfo);
		Set<Object> set = object.keySet();
		for (Object object2 : set) {
			dbcontent.eq(object2.toString(), object.get(object2.toString()));
		}
		return dbcontent.limit(20).select();
	}

	@SuppressWarnings("unchecked")
	public JSONObject page(int idx, int pageSize) {
		JSONArray array = dbcontent.page(idx, pageSize);
		JSONObject object = new JSONObject();
		object.put("totalSize", (int) Math.ceil((double) dbcontent.count() / pageSize));
		object.put("currentPage", idx);
		object.put("pageSize", pageSize);
		object.put("data", array);
		return object;
	}

	@SuppressWarnings("unchecked")
	public JSONObject page(int idx, int pageSize, JSONObject GroupInfo) {
		for (Object object2 : GroupInfo.keySet()) {
			if (GroupInfo.containsKey("_id")) {
				dbcontent.eq("_id", new ObjectId(GroupInfo.get("_id").toString()));
			}
			dbcontent.eq(object2.toString(), GroupInfo.get(object2.toString()));
		}
		JSONArray array = dbcontent.page(idx, pageSize);
		JSONObject object = new JSONObject();
		object.put("totalSize", (int) Math.ceil((double) dbcontent.count() / pageSize));
		object.put("currentPage", idx);
		object.put("pageSize", pageSize);
		object.put("data", array);
		return object;
	}

	@SuppressWarnings("unchecked")
	public int setfatherid(String ogid, int fatherid) {
		JSONObject object = new JSONObject();
		object.put("fatherid", fatherid);
		return dbcontent.eq("_id", new ObjectId(ogid)).data(object).update() != null ? 0 : 99;
	}

	@SuppressWarnings("unchecked")
	public int setsort(String ogid, int num) {
		JSONObject object = new JSONObject();
		object.put("sort", num);
		return dbcontent.eq("_id", new ObjectId(ogid)).data(object).update() != null ? 0 : 99;
	}

	@SuppressWarnings("unchecked")
	public int setTempId(String ogid, String tempid) {
		JSONObject object = new JSONObject();
		object.put("tempid", tempid);
		return dbcontent.eq("_id", new ObjectId(ogid)).data(object).update() != null ? 0 : 99;
	}

	@SuppressWarnings("unchecked")
	public int setslevel(String ogid, int slevel) {
		JSONObject object = new JSONObject();
		object.put("slevel", slevel);
		return dbcontent.eq("_id", new ObjectId(ogid)).data(object).update() != null ? 0 : 99;
	}

	public int delete(String[] arr) {
		dbcontent.or();
		for (int i = 0; i < arr.length; i++) {
			dbcontent.eq("_id", new ObjectId(arr[i]));
		}
		return dbcontent.deleteAll() == arr.length ? 0 : 99;
	}

	public boolean check_name(String name) {
		return (name.length() > 0 && name.length() <= 20);
	}

	public String findByFatherid(String fatherid) {
		String name = null;
		JSONArray array = dbcontent.eq("fatherid", fatherid).select();
		for (Object object : array) {
			JSONObject _obj = (JSONObject) object;
			name = _obj.get("name").toString();
		}
		return name;
	}

	public JSONArray getColumnByFid(String ogid) {
		return dbcontent.eq("fatherid", ogid).select();
	}

	/**
	 * 生成32位随机编码
	 * 
	 * @return
	 */
	public static String getID() {
		String str = UUID.randomUUID().toString().trim();
		return str.replace("-", "");
	}

	/**
	 * 将map添加至JSONObject中
	 * 
	 * @param map
	 * @param object
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public JSONObject AddMap(HashMap<String, Object> map, JSONObject object) {
		if (map.entrySet() != null) {
			Iterator<Entry<String, Object>> iterator = map.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<String, Object> entry = (Map.Entry<String, Object>) iterator.next();
				if (!object.containsKey(entry.getKey())) {
					object.put(entry.getKey(), entry.getValue());
				}
			}
		}
		return object;
	}

	public String resultMessage(int num, String msg) {
		String message = null;
		switch (num) {
		case 0:
			message = msg;
			break;
		case 1:
			message = "内容组名称长度不合法";
			break;
		case 2:
			message = "必填项没有填";
			break;
		case 3:
			message = "不允许重复添加";
			break;
		default:
			message = "其他异常";
		}
		return jGrapeFW_Message.netMSG(num, message);
	}
}
