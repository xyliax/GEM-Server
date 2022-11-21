package comp4342.group15.gemserver.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import comp4342.group15.gemserver.dao.MySqlRepository;
import comp4342.group15.gemserver.model.MapBox;
import comp4342.group15.gemserver.model.Post;
import comp4342.group15.gemserver.model.Response;
import comp4342.group15.gemserver.model.User;
import org.apache.tomcat.util.codec.binary.Base64;
import org.imgscalr.Scalr;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static comp4342.group15.gemserver.model.Response.*;
import static org.imgscalr.Scalr.OP_ANTIALIAS;

@RestController
public final class GEMController {
    @Resource
    private MySqlRepository mySqlRepository;

    @GetMapping("/daemon")
    public void daemon(@RequestParam("code") String code) {
        if (!code.equals("comp4342")) return;
        mySqlRepository.select1();
        System.out.println("DAEMON");
    }

    @GetMapping("/user")
    public User user(@RequestParam("username") String userName) {
        return mySqlRepository.retrieveUserByName(userName);
    }

    @GetMapping("/post")
    public Post post(@RequestParam("id") String postId) {
        return mySqlRepository.retrievePostById(postId);
    }

    @PostMapping("/register")
    public Response userRegister(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        if (username == null || password == null)
            return bad("Bad request. Need username and password!");
        if (mySqlRepository.retrieveUserByName(username) != null)
            return fail(String.format("'%s' exists!", username));
        mySqlRepository.insertUser(new User("", username, password, "", "1"));
        return success(String.format("Register successfully! - %s", username));
    }

    @PostMapping("/login")
    public Response userLogin(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        if (username == null || password == null)
            return bad("Bad request. Need 'username' and 'password'!");
        User user = mySqlRepository.retrieveUserByName(username);
        if (user == null)
            return fail(String.format("Cannot find user '%s'!", username));
        if (!user.getPassword().equals(password))
            return fail("Wrong password!");
        user = mySqlRepository.updateUser(user);
        assert user != null;
        return success(user.getIdentifier());
    }

    @PostMapping("/upload")
    public Response uploadPost(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String identifier = request.get("identifier");
        String message = request.get("message");
        String location = request.get("location");
        String pictureRaw = request.get("picture");
        if (username == null || identifier == null || message == null || location == null || pictureRaw == null)
            return bad("Bad request. Need 'username' and 'identifier' and 'message' and 'location' and 'picture");
        User user = mySqlRepository.retrieveUserByName(username);
        if (user == null)
            return fail(String.format("Cannot find user '%s'!", username));
        if (!user.getIdentifier().equals(identifier))
            return fail("Session Expired!");
        String picName = String.format("%s@%s", username, new Date().toString().replace(' ', '-'));
        byte[] bytes = Base64.decodeBase64(pictureRaw);
        File picPath = new File("/opt/GEM-Server/public/picture");
        File tbnPath = new File("/opt/GEM-Server/public/thumbnail");
        if (!picPath.exists()) picPath.mkdirs();
        if (!tbnPath.exists()) tbnPath.mkdirs();
        try (OutputStream stream = new FileOutputStream(new File(picPath, picName))) {
            stream.write(bytes);
        } catch (IOException ioException) {
            throw new RuntimeException(ioException);
        }
        try (OutputStream stream = new FileOutputStream(new File(tbnPath, picName))) {
            ByteArrayOutputStream thumbOutput = new ByteArrayOutputStream();
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            BufferedImage thumbImg = Scalr.resize(img, Scalr.Method.AUTOMATIC, Scalr.Mode.AUTOMATIC, 100, OP_ANTIALIAS);
            ImageIO.write(thumbImg, "png", thumbOutput);
            stream.write(thumbOutput.toByteArray());
        } catch (IOException ioException) {
            throw new RuntimeException(ioException);
        }
        mySqlRepository.insertPost(new Post("", user.getUserId(), message, location, "", picName));
        return success("Posted!");
    }

    @GetMapping("/trend")
    public List<Map<String, Object>> trend() {
        return mySqlRepository.retrieveTrend();
    }

    @GetMapping(value = "/map", produces = MediaType.TEXT_HTML_VALUE)
    public String showMap(@RequestParam("X") String x, @RequestParam("Y") String y) {
        return MapBox.getHTML(x, y, mySqlRepository.retrieveTrend());
    }

    @GetMapping(value = "/loc")
    public String showLoc(@RequestParam("X") String x, @RequestParam("Y") String y) {
        ResponseEntity<String> response = new RestTemplate().getForEntity(MapBox.getLocation(x, y), String.class);
        String body = response.getBody();
        JSONObject jsonObject = JSON.parseObject(body);
        if (jsonObject == null || !jsonObject.get("status").equals("1")) return "Unknown";
        if (jsonObject.get("regeocode") == null) return "Unknown";
        String geo = jsonObject.get("regeocode").toString();
        JSONObject regeocode = JSON.parseObject(geo);
        String loc = regeocode.get("formatted_address").toString();
        return loc == null ? "Unknown" : loc;
    }
}
