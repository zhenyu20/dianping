package com.dp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.dto.LoginFormDTO;
import com.dp.dto.Result;
import com.dp.dto.UserDTO;
import com.dp.entity.User;
import com.dp.mapper.UserMapper;
import com.dp.service.IUserService;
import com.dp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;;
import cn.hutool.core.bean.BeanUtil;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.dp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author zy
 * @since 2023-12-06
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {//校验手机号
            return Result.fail("手机号格式错误！");
        }
        String code = RandomUtil.randomNumbers(6);//生成验证码
        session.setAttribute("code",code);//保存在session中
        log.debug("验证码发送成功！{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {//校验手机号
            return Result.fail("手机号格式错误！");
        }
        String cacheCode = (String) session.getAttribute("code");
        String code = loginForm.getCode();
        if(code == null || !code.equals(cacheCode)){
            return Result.fail("验证码错误！");
        }
        User user = query().eq("phone", loginForm.getPhone()).one();//根据手机号查询用户
        if(user == null){
            user = createUserByPhone(phone);
        }

//        session.setAttribute("user",user);//保存在session中
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok();//下次验证cookie
    }

    private User createUserByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
