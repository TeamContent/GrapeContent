package model;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;

import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import apps.appsProxy;
import database.db;
import esayhelper.DBHelper;
import esayhelper.JSONHelper;
import esayhelper.StringHelper;
import esayhelper.formHelper;
import esayhelper.jGrapeFW_Message;
import esayhelper.formHelper.formdef;
import jodd.util.ArraysUtil;
import security.codec;

@SuppressWarnings("unchecked")
public class ContentModel {
	private static DBHelper dbcontent;
	private static formHelper _form;
	private JSONObject _obj = new JSONObject();

	static {
		System.out.println(appsProxy.configValue());
		System.out.println(appsProxy.appid());
		dbcontent = new DBHelper(appsProxy.configValue().get("db").toString(), "objectList");
		_form = dbcontent.getChecker();
	}

	public ContentModel() {
		_form.putRule("mainName", formdef.notNull);
		_form.putRule("content", formdef.notNull);
		_form.putRule("wbid", formdef.notNull);
	}

	private db bind() {
		System.out.println(appsProxy.appid());
		System.out.println(appsProxy.configValue());
		return dbcontent.bind(String.valueOf(appsProxy.appid()));
	}

	/**
	 * 发布文章
	 * 
	 * @param content
	 * @return && 1：判断字段是否合法 && 2：必填信息没有填 && 3：同栏目下已存在该文章 && 4：同站点下已存在该文章 &&
	 *         5：是否含有敏感词 接入第三方插件
	 */
	// 不允许重复添加，但存在同名不同内容的文章
	public String insert(JSONObject content) {
		if (!_form.checkRuleEx(content)) {
			return resultMessage(2, "");
		}
		if (content.get("mainName").toString().equals("")) {
			return resultMessage(1, "");
		}
		if (!content.get("fatherid").toString().equals("0")) {
			content.remove("ogid");
		}
		if (content.containsKey("image")) {
			if (content.get("image").toString().contains(":")) {
				content.put("image", getimage(content));
			}
		}
		String info = bind().data(content).insertOnce().toString();
		return resultMessage(findByOid(info));
	}

	public int UpdateArticle(String oid, JSONObject content) {
		if (content.containsKey("mainName")) {
			if (content.get("mainName").toString().equals("")) {
				return 1;
			}
		}
		if (content.containsKey("_id")) {
			content.remove("_id");
		}
		if (content.containsKey("image")) {
			String image = content.get("image").toString();
			if (image.contains("upload")) {
				if (image.contains("8080")) {
					content.put("image", getimage(content));
				}
			}
		}
		return bind().eq("_id", new ObjectId(oid)).data(content).update() != null ? 0 : 99;
	}

	public int DeleteArticle(String oid) {
		return bind().eq("_id", new ObjectId(oid)).delete() != null ? 0 : 99;
	}

	/**
	 * 删除指定栏目下的指定文章
	 * 
	 * @param oid
	 *            文章id
	 * @param ogid
	 *            栏目id
	 */
	public int deleteByOgID(String oid, String ogid) {
		// 查询oid对应的文章
		JSONObject _obj = (JSONObject) bind().eq("_id", new ObjectId(oid)).select().get(0);
		// 获取栏目id
		String values = _obj.get("ogid").toString();
		values = values.replace(ogid, "");
		JSONObject obj = new JSONObject();
		obj.put("ogid", values);
		return bind().eq("_id", new ObjectId(oid)).data(obj).update() != null ? 0 : 99;
	}

	/**
	 * 删除指定站点下的指定文章
	 * 
	 * @param oid
	 *            文章id
	 * @param wbid
	 *            站点id
	 * @return
	 */
	public int deleteByWbID(String oid, String wbid) {
		// 查询oid对应的文章
		JSONObject _obj = (JSONObject) bind().eq("_id", new ObjectId(oid)).select().get(0);
		// 获取站点id
		String values = _obj.get("wbid").toString();
		values = values.replace(wbid, "");
		JSONObject obj = new JSONObject();
		obj.put("wbid", values);
		return bind().eq("_id", new ObjectId(oid)).data(obj).update() != null ? 0 : 99;
	}

	/**
	 * 文章已存在，设置栏目
	 * 
	 * @param oid
	 * @param ogid
	 * @return
	 */
	public int setGroup(String oid, String ogid) {
		// 查询oid对应的文章
		JSONObject _obj = (JSONObject) select(oid).get(0);
		// 获取栏目id
		String[] value = _obj.get("ogid").toString().split(",");
		// 判断该栏目是否存在
		if (ArraysUtil.contains(value, ogid)) {
			return 3; // 返回3 文章已存在于该栏目下
		}
		String values = StringHelper.join(ArraysUtil.append(value, ogid));
		JSONObject obj = new JSONObject();
		obj.put("ogid", values);
		return bind().eq("_id", new ObjectId(oid)).data(obj).update() != null ? 0 : 99;
	}

	public int setGroup(String ogid) {
		String cont = "{\"ogid\":0}";
		if (ogid.contains(",")) {
			String[] value = ogid.split(",");
			int len = value.length;
			bind().or();
			for (int i = 0; i < len; i++) {
				bind().eq("ogid", value[i]);
			}
		} else {
			bind().eq("ogid", ogid);
		}
		return bind().data(cont).updateAll() != 0 ? 0 : 99;
	}

	public String page(int idx, int pageSize) {
		JSONArray array = bind().page(idx, pageSize);
		JSONArray array2 = dencode(array);
		JSONObject object = new JSONObject();
		object.put("totalSize", (int) Math.ceil((double) bind().count() / pageSize));
		object.put("currentPage", idx);
		object.put("pageSize", pageSize);
		object.put("data", join(getImg(array2)));
		return resultMessage(object);
	}

	public String page(int idx, int pageSize, JSONObject content) {
		System.out.println("idx:"+idx+",pageSize:"+pageSize+",content:"+content);
		JSONObject object = new JSONObject();
		if (content == null) {
			return resultMessage(0, "");
		}
		for (Object object2 : content.keySet()) {
			if (content.containsKey("_id")) {
				bind().eq("_id", new ObjectId(content.get("_id").toString()));
			}
			bind().eq(object2.toString(), content.get(object2.toString()));
		}
		JSONArray array = bind().dirty().desc("time").page(idx, pageSize);
		JSONArray array2 = dencode(array);
//		System.out.println("cc"+(int) Math.ceil((double) bind().count() / pageSize));
		object.put("totalSize", (int) Math.ceil((double) bind().count() / pageSize));
		object.put("currentPage", idx);
		object.put("pageSize", pageSize);
		object.put("data", join(getImg(array2)));
		return resultMessage(object);
	}

	@SuppressWarnings("unchecked")
	private JSONArray dencode(JSONArray array) {
		if (array.size() == 0) {
			return array;
		}
		JSONArray arry = new JSONArray();
		for (int i = 0; i < array.size(); i++) {
			JSONObject object = (JSONObject) array.get(i);
			if (object.containsKey("content") && object.get("content") != "") {
				object.put("content", codec.decodebase64(object.get("content").toString()));
			}
			arry.add(object);
		}
		return arry;
	}

	@SuppressWarnings("unchecked")
	private JSONObject dencode(JSONObject obj) {
		obj.put("content", codec.decodebase64(obj.get("content").toString()));
		return obj;
	}

	public JSONObject select(String oid) {
		if (oid == null||("").equals(oid)) {
			return null;
		}
//		JSONObject object = bind().eq("_id", new ObjectId(oid)).find();
		JSONObject object = findByOid(oid);
		JSONObject preobj = find(object.get("time").toString(), "<");
		JSONObject nextobj = find(object.get("time").toString(), ">");
		object.put("previd", getpnArticle(preobj).get("id"));
		object.put("prevname", getpnArticle(preobj).get("name"));
		object.put("nextid", getpnArticle(nextobj).get("id"));
		object.put("nextname", getpnArticle(nextobj).get("name"));
		return join(getImg(object));
	}

	public String findnew() {
		JSONObject object = bind().desc("time").desc("_id").find();
		return resultMessage(join(getImg(object)));
	}

	public JSONArray searchByUid(String uid) {
		JSONArray array = bind().eq("ownid", uid).limit(30).select();
		return array;
	}

	public int updatesort(String oid, int sortNo) {
		JSONObject object = new JSONObject();
		object.put("sort", sortNo);
		return bind().eq("_id", new ObjectId(oid)).data(object).update() != null ? 0 : 99;
	}

	public JSONArray search(JSONObject condString) {
		if (condString.containsKey("time")) {

		}
		bind().and();
		for (Object object2 : condString.keySet()) {
			bind().like(object2.toString(), condString.get(object2.toString()));
		}
		JSONArray array = bind().limit(30).select();
		return join(getImg(array));
	}

	// 获取积分价值条件？？
	public void getpoint() {
		bind().field("point").limit(20).select();
	}

	// 根据文章id查询文章
	public JSONObject findByOid(String oid) {
		JSONObject object = bind().eq("_id", new ObjectId(oid)).find();
		object = dencode(object);
		return join(getImg(object));
	}

	// 根据栏目id查询文章
	public JSONArray findByGroupID(String ogid) {
		JSONArray array = bind().desc("time").eq("ogid", ogid).limit(20).select();
		array = getImg(array);
		return join(array);
	}

	public JSONArray findByGroupID(String ogid, int no) {
		// 通过模糊查询，查询出该ogid对应的文章
		JSONArray array = bind().eq("ogid", ogid).limit(no).select();
		return getImg(array);
	}

	// 修改tempid
	public int setTempId(String oid, String tempid) {
		JSONObject object = new JSONObject();
		object.put("tempid", tempid);
		return bind().eq("_id", new ObjectId(oid)).data(object).update() != null ? 0 : 99;
	}

	// 修改fatherid，同时删除ogid（ogid=""）
	public int setfatherid(String oid, String fatherid) {
		JSONObject object = new JSONObject();
		if (fatherid == null) {
			fatherid = "0";
		} else {
			object.put("ogid", "");
		}
		object.put("fatherid", fatherid);
		return bind().eq("_id", new ObjectId(oid)).data(object).update() != null ? 0 : 99;
	}

	// 修改对象密级
	public int setslevel(String oid, String slevel) {
		JSONObject object = new JSONObject();
		object.put("tempid", slevel);
		return bind().eq("_id", new ObjectId(oid)).data(object).update() != null ? 0 : 99;
	}

	// 文章审核 state：0 草稿，1 待审核，2 审核通过 3 审核不通过
	public int review(String oid, String managerid, String state) {
		JSONObject object = new JSONObject();
		object.put("manageid", managerid);
		object.put("state", state);
		return bind().eq("_id", new ObjectId(oid)).data(object).update() != null ? 0 : 99;
	}

	public int delete(String[] arr) {
		bind().or();
		for (int i = 0; i < arr.length; i++) {
			bind().eq("_id", new ObjectId(arr[i]));
		}
		return bind().deleteAll() == arr.length ? 0 : 99;
	}

	public JSONArray find(String ogid, int no) {
		JSONArray array = bind().eq("ogid", ogid).limit(no).field("image,desp").select();
		return array;
	}

	public JSONArray find(String[] ogid, int no) {
		bind().or();
		for (int i = 0; i < ogid.length; i++) {
			bind().eq("ogid", ogid[i]);
		}
		return bind().limit(no).select();
	}

	public JSONObject find(String time, String logic) {
		if (time.contains("$numberLong")) {
			JSONObject object = JSONHelper.string2json(time);
			time = object.get("$numberLong").toString();
		}
		if (logic == "<") {
			bind().lt("time", time).desc("time");
		} else {
			bind().gt("time", time).asc("time");
		}
		return bind().find();
	}

	private JSONObject getpnArticle(JSONObject object) {
		String id = null;
		String name = null;
		JSONObject object2 = new JSONObject();
		if (object != null) {
			JSONObject obj = (JSONObject) object.get("_id");
			id = obj.get("$oid").toString();
			name = object.get("mainName").toString();
			object2.put("id", id);
			object2.put("name", name);
		}
		return object2;
	}

	private String getimage(JSONObject object) {
		String image = object.get("image").toString();
		return image.split("8080")[1];
	}

	// 获取栏目id及名称
	public List<JSONObject> getName(List<JSONObject> list, JSONObject object) {
		JSONObject obj = new JSONObject();
		JSONObject objID = (JSONObject) object.get("_id");
		obj.put("_id", objID.get("$oid").toString());
		obj.put("name", object.get("name").toString());
		list.add(obj);
		return list;

	}

	// 根据自定义条件进行统计
	public String getCount(JSONObject object) {
		for (Object obj : object.keySet()) {
			bind().like(obj.toString(), object.get(obj.toString()).toString());
		}
		return resultMessage(0, String.valueOf(bind().count()));
	}

	// 获取图片内容
	private JSONObject getImg(JSONObject object) {
		String imgURL = object.get("image").toString();
		if (imgURL.contains("File")) {
			imgURL = getAppIp("file").split("/")[1] + imgURL;
			object.put("image", imgURL);
		}
		return object;
	}

	private JSONArray getImg(JSONArray array) {
		if (array.size() == 0) {
			return array;
		}
		JSONArray array2 = new JSONArray();
		for (int i = 0, len = array.size(); i < len; i++) {
			JSONObject object = (JSONObject) array.get(i);
			if (object.containsKey("image")) {
				String imgURL = object.get("image").toString();
				if (imgURL.contains("upload")) {
					System.out.println(getAppIp("file"));
					imgURL = "http://" + getAppIp("file").split("/")[1] + imgURL;
					object.put("image", imgURL);
				}
			}
			array2.add(object);
		}

		return array2;
	}

	private JSONArray join(JSONArray array) {
		JSONArray arrays = new JSONArray();
		for (int i = 0, len = array.size(); i < len; i++) {
			JSONObject object = (JSONObject) array.get(i);
			if (object.get("tempid").toString().equals("0")) {
				object.put("tempContent", null);
			} else {
				String temp = appsProxy.proxyCall(getAppIp("host").split("/")[0],
						appsProxy.appid() + "/19/TemplateContext/TempFindByTid/s:" + object.get("tempid").toString(),
						null, "").toString();
				object.put("tempContent", temp);
			}
			arrays.add(object);
		}
		return arrays;
	}

	private JSONObject join(JSONObject object) {
		if (object.get("tempid").toString().equals("0")) {
			object.put("tempContent", null);
		} else {
			String temp = appsProxy.proxyCall(getAppIp("host").split("/")[0],
					appsProxy.appid() + "/19/TemplateContext/TempFindByTid/s:" + object.get("tempid").toString(), null,
					"").toString();
			object.put("tempContent", temp);
		}
		return object;
	}

	public String showstate(String state) {
		String msg = "";
		switch (state) {
		case "1":
			msg = "待审核";
			break;
		case "2":
			msg = "终审通过";
			break;
		case "3":
			msg = "终审不通过";
			break;
		default:
			msg = "草稿";
			break;
		}
		return msg;
	}
	private String getAppIp(String key) {
		String value = "";
		try {
			Properties pro = new Properties();
			pro.load(new FileInputStream("URLConfig.properties"));
			value = pro.getProperty(key);
		} catch (Exception e) {
			value = "";
		}
		return value;
	}

	/**
	 * 将map添加至JSONObject中
	 * 
	 * @param map
	 * @param object
	 * @return
	 */
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

	public String resultMessage(JSONObject object) {
		_obj.put("records", object);
		return resultMessage(0, _obj.toString());
	}

	public String resultMessage(JSONArray array) {
		_obj.put("records", array);
		return resultMessage(0, _obj.toString());
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
			message = "该栏目已存在本文章";
			break;
		case 4:
			message = "该站点已存在本文章";
			break;
		case 5:
			message = "存在敏感词";
			break;
		case 6:
			message = "超过限制字数";
			break;
		case 7:
			message = "没有创建数据权限，请联系管理员进行权限调整";
			break;
		case 8:
			message = "没有修改数据权限，请联系管理员进行权限调整";
			break;
		case 9:
			message = "没有删除数据权限，请联系管理员进行权限调整";
			break;
		default:
			message = "其他异常";
		}
		return jGrapeFW_Message.netMSG(num, message);
	}
}
