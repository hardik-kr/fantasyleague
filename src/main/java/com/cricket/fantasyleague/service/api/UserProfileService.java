package com.cricket.fantasyleague.service.api;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.payload.response.UserProfileResponse;

public interface UserProfileService {

    UserProfileResponse getProfile(User user);
}
