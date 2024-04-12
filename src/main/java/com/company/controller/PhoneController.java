package com.company.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.concurrent.TimeUnit;

@Slf4j
@Controller
@CrossOrigin
public class PhoneController {

    @Autowired
    private RedisTemplate redisTemplate;

    // 生成验证码（4位）
    public int generatorCode(){
        int num = (int) (Math.random()*10000);
        return num;
    }

    // 获取访客ip地址
    public String getAccessIP(HttpServletRequest request){
        return request.getRemoteAddr();
    }

    // 前端保证了手机号11位
    @ResponseBody   // 设置当前控制器方法响应内容为当前返回值，无需解析
    @GetMapping("/getValidateCode")  // 测试：localhost:8080/getValidateCode?phoneNumber=12912345678
    public String getValidateCode(@RequestParam("phoneNumber") String phoneNumber, HttpServletRequest request){
        // 验证保护，每个ip地址5分钟内只能验证3次，超过则锁定12小时
        String res = this.valip(this.getAccessIP(request));
        if(!res.equals("success")){
            return res;
        }

        ValueOperations valueOperations = redisTemplate.opsForValue();
        String key = "phone:code:"+phoneNumber;
        String phoneCode = (String) valueOperations.get(key);
        if(phoneCode == null){
            int new_phoneCode = generatorCode();
            log.info("手机号已发送短信API接口，验证码是："+new_phoneCode);
            valueOperations.set(key, new_phoneCode+"", 60, TimeUnit.SECONDS);
            return "验证码发送成功，请在手机查看短信";
        }else{
            Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);// 获取剩余时间
            log.info("获取失败，请勿重复获取，请耐心等待" + expire.toString() + "秒");
            return "获取失败，请勿重复获取，请耐心等待" + expire.toString() + "秒";
        }
    }

    @ResponseBody   // 设置当前控制器方法响应内容为当前返回值，无需解析
    @GetMapping("/validate")  // 测试：localhost:8080/getValidateCode?phoneNumber=12912345678
    public String validate(@RequestParam("phoneCode") String phoneCode, @RequestParam("phoneNumber") String phoneNumber, HttpServletRequest request){
        // 验证保护，每个ip地址5分钟内只能验证3次，超过则锁定12小时
        String res = this.valip(this.getAccessIP(request));
        if(!res.equals("success")){
            return res;
        }

        String key = "phone:code:"+phoneNumber;
        ValueOperations valueOperations = redisTemplate.opsForValue();
        String phoneCode_redis = (String) valueOperations.get(key);
        if(phoneCode.equals(phoneCode_redis)){
            redisTemplate.delete(key);
            log.info("验证成功");
            return "验证成功";
        }else{
            log.info("验证失败");
            return "获取失败";
        }
    }

    public String valip(String accessIP){
        ValueOperations valueOperations = redisTemplate.opsForValue();

        // 验证保护，每个ip地址5分钟内只能验证3次，超过则锁定12小时
        String key_ip = "phone:code:"+accessIP;
        Integer val_time = (Integer) valueOperations.get(key_ip);
        if(val_time==null){
            valueOperations.set(key_ip, 1, 300, TimeUnit.SECONDS);   // 5分钟内验证若超过3次
        }else{
            if(val_time==-1){
                Long expire = redisTemplate.getExpire(key_ip, TimeUnit.HOURS);// 获取剩余时间
                return "ip将被锁定"+expire.toString()+"小时";
            }
            if(val_time<3){
                Long expire = redisTemplate.getExpire(key_ip, TimeUnit.SECONDS);// 获取剩余时间
                valueOperations.set(key_ip, val_time+1, expire, TimeUnit.SECONDS);
            }else{  // 第3次
                valueOperations.set(key_ip, -1, 12, TimeUnit.HOURS);  // 锁定12小时
            }
        }
        return "success";
    }

}
