package comp4342.group15.gemserver.controller;

import comp4342.group15.gemserver.dao.MySqlRepository;
import comp4342.group15.gemserver.model.MapBox;
import comp4342.group15.gemserver.model.Post;
import comp4342.group15.gemserver.model.Response;
import comp4342.group15.gemserver.model.User;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static comp4342.group15.gemserver.model.Response.*;

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
        mySqlRepository.insertPost(new Post("", user.getUserId(), message, location, "", picName));
        byte[] bytes = Base64.decodeBase64(pictureRaw);
        File picPath = new File("/opt/GEM-Server/public/picture");
        File tbnPath = new File("/opt/GEM-Server/public/thumbnail");
        if (!picPath.exists()) picPath.mkdirs();
        if (!tbnPath.exists()) tbnPath.mkdirs();
        try (OutputStream stream = new FileOutputStream(new File(picPath, picName))) {
            stream.write(bytes);
        } catch (IOException ignored) {
        }
        try (OutputStream stream = new FileOutputStream(new File(tbnPath, picName))) {
            stream.write(bytes);
        } catch (IOException ignored) {
        }
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
}
