package com.ra.rabnbserver.server.gold;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ra.rabnbserver.VO.gold.GoldQuantHomeVO;
import com.ra.rabnbserver.dto.gold.AdminGoldQuantAccountQueryDTO;
import com.ra.rabnbserver.dto.gold.AdminGoldQuantWindowQueryDTO;
import com.ra.rabnbserver.pojo.GoldQuantAccount;
import com.ra.rabnbserver.pojo.GoldQuantWindow;

public interface GoldQuantServe extends IService<GoldQuantWindow> {
    GoldQuantHomeVO getHome(Long userId);

    void payHosting(Long userId);

    void buyWindow(Long userId, Integer quantity);

    void renewWindow(Long userId, Long windowId);

    void batchRenew(Long userId, Integer quantity);

    IPage<GoldQuantAccount> getAdminAccountPage(AdminGoldQuantAccountQueryDTO query);

    IPage<GoldQuantWindow> getAdminWindowPage(AdminGoldQuantWindowQueryDTO query);
}
