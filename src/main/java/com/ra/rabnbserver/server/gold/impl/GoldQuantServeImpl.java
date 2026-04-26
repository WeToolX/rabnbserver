package com.ra.rabnbserver.server.gold.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.VO.gold.GoldQuantHomeVO;
import com.ra.rabnbserver.VO.gold.GoldQuantSettingsVO;
import com.ra.rabnbserver.VO.gold.GoldQuantWindowVO;
import com.ra.rabnbserver.dto.gold.AdminGoldQuantAccountQueryDTO;
import com.ra.rabnbserver.dto.gold.AdminGoldQuantWindowQueryDTO;
import com.ra.rabnbserver.enums.BillType;
import com.ra.rabnbserver.enums.FundType;
import com.ra.rabnbserver.enums.TransactionType;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.mapper.GoldQuantAccountMapper;
import com.ra.rabnbserver.mapper.GoldQuantWindowMapper;
import com.ra.rabnbserver.mapper.UserMapper;
import com.ra.rabnbserver.mapper.UserMinerMapper;
import com.ra.rabnbserver.pojo.GoldQuantAccount;
import com.ra.rabnbserver.pojo.GoldQuantWindow;
import com.ra.rabnbserver.pojo.User;
import com.ra.rabnbserver.pojo.UserMiner;
import com.ra.rabnbserver.server.gold.GoldQuantServe;
import com.ra.rabnbserver.server.sys.SystemConfigServe;
import com.ra.rabnbserver.server.user.UserBillServe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GoldQuantServeImpl extends ServiceImpl<GoldQuantWindowMapper, GoldQuantWindow> implements GoldQuantServe {
    private static final String SETTINGS_KEY = "GOLD_QUANT_SETTINGS";

    private final GoldQuantAccountMapper accountMapper;
    private final UserMapper userMapper;
    private final UserMinerMapper userMinerMapper;
    private final UserBillServe userBillServe;
    private final SystemConfigServe systemConfigServe;

    @Override
    public GoldQuantHomeVO getHome(Long userId) {
        User user = getUser(userId);
        GoldQuantSettingsVO settings = getSettings();
        GoldQuantAccount account = getAccount(userId);
        List<GoldQuantWindow> windows = listUserWindows(userId);

        GoldQuantHomeVO vo = new GoldQuantHomeVO();
        vo.setHostingExpireTime(account == null ? null : account.getHostingExpireTime());
        vo.setBalance(user.getBalance() == null ? BigDecimal.ZERO : user.getBalance());
        vo.setHostingFee(settings.getHostingFee());
        vo.setWindowMaintenanceFee(settings.getWindowMaintenanceFee());
        vo.setMinerCount(countUserMiners(userId));
        vo.setWindowCount(windows.size());
        vo.setMaxCanBuyCount(Math.max(0, settings.getMaxWindowCount() - windows.size()));
        vo.setNearestWindowMaintenanceExpireTime(windows.stream()
                .map(GoldQuantWindow::getMaintenanceExpireTime)
                .filter(time -> time != null)
                .min(LocalDateTime::compareTo)
                .orElse(null));
        vo.setWindows(buildWindowVOList(windows));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void payHosting(Long userId) {
        User user = getUser(userId);
        GoldQuantSettingsVO settings = getSettings();
        LocalDateTime now = LocalDateTime.now();
        userBillServe.createBillAndUpdateBalance(
                userId,
                settings.getHostingFee(),
                BillType.PLATFORM,
                FundType.EXPENSE,
                TransactionType.GOLD_QUANT,
                "黄金量化托管费",
                "GQ_HOST_" + IdWorker.getIdStr(),
                null,
                null,
                1,
                null
        );

        GoldQuantAccount account = getAccountForUpdate(userId);
        LocalDateTime base = account == null || account.getHostingExpireTime() == null || account.getHostingExpireTime().isBefore(now)
                ? now
                : account.getHostingExpireTime();
        LocalDateTime newExpireTime = base.plusDays(settings.getHostingDays());
        if (account == null) {
            account = new GoldQuantAccount();
            account.setUserId(userId);
            account.setWalletAddress(user.getUserWalletAddress());
            account.setHostingExpireTime(newExpireTime);
            accountMapper.insert(account);
        } else {
            accountMapper.update(null, new LambdaUpdateWrapper<GoldQuantAccount>()
                    .eq(GoldQuantAccount::getId, account.getId())
                    .set(GoldQuantAccount::getWalletAddress, user.getUserWalletAddress())
                    .set(GoldQuantAccount::getHostingExpireTime, newExpireTime));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void buyWindow(Long userId, Integer quantity) {
        validateQuantity(quantity);
        User user = getUser(userId);
        GoldQuantSettingsVO settings = getSettings();
        int minerCount = countUserMiners(userId);
        if (minerCount < settings.getMinerThreshold()) {
            throw new BusinessException("持有矿机数不足10");
        }

        int currentWindowCount = countUserWindows(userId);
        if (currentWindowCount + quantity > settings.getMaxWindowCount()) {
            throw new BusinessException("量化窗口数量不能超过" + settings.getMaxWindowCount());
        }

        BigDecimal amount = settings.getWindowMaintenanceFee().multiply(BigDecimal.valueOf(quantity));
        userBillServe.createBillAndUpdateBalance(
                userId,
                amount,
                BillType.PLATFORM,
                FundType.EXPENSE,
                TransactionType.GOLD_QUANT,
                "黄金量化窗口维护费 x" + quantity,
                "GQ_BUY_" + IdWorker.getIdStr(),
                null,
                null,
                quantity,
                null
        );

        LocalDateTime expireTime = LocalDateTime.now().plusDays(settings.getMaintenanceDays());
        List<GoldQuantWindow> windows = new ArrayList<>();
        for (int i = 1; i <= quantity; i++) {
            GoldQuantWindow window = new GoldQuantWindow();
            window.setUserId(userId);
            window.setWalletAddress(user.getUserWalletAddress());
            window.setWindowNo("GQW_" + userId + "_" + (currentWindowCount + i));
            window.setMaintenanceExpireTime(expireTime);
            window.setStatus(1);
            windows.add(window);
        }
        this.saveBatch(windows);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void renewWindow(Long userId, Long windowId) {
        if (windowId == null) {
            throw new BusinessException("窗口ID不能为空");
        }
        GoldQuantWindow window = this.getOne(new LambdaQueryWrapper<GoldQuantWindow>()
                .eq(GoldQuantWindow::getId, windowId)
                .eq(GoldQuantWindow::getUserId, userId)
                .last("FOR UPDATE"));
        if (window == null) {
            throw new BusinessException("量化窗口不存在");
        }
        GoldQuantSettingsVO settings = getSettings();
        userBillServe.createBillAndUpdateBalance(
                userId,
                settings.getWindowMaintenanceFee(),
                BillType.PLATFORM,
                FundType.EXPENSE,
                TransactionType.GOLD_QUANT,
                "黄金量化窗口续费 windowId=" + windowId,
                "GQ_RENEW_" + IdWorker.getIdStr(),
                null,
                null,
                1,
                null
        );
        renewLockedWindows(List.of(window), settings);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void batchRenew(Long userId, Integer quantity) {
        validateQuantity(quantity);
        int windowCount = countUserWindows(userId);
        if (quantity > windowCount) {
            throw new BusinessException("续费数量不能超过已购买窗口数量");
        }
        List<GoldQuantWindow> windows = this.list(new LambdaQueryWrapper<GoldQuantWindow>()
                .eq(GoldQuantWindow::getUserId, userId)
                .eq(GoldQuantWindow::getStatus, 1)
                .orderByAsc(GoldQuantWindow::getMaintenanceExpireTime)
                .orderByAsc(GoldQuantWindow::getId)
                .last("LIMIT " + quantity + " FOR UPDATE"));
        if (windows.size() < quantity) {
            throw new BusinessException("可续费窗口数量不足");
        }

        GoldQuantSettingsVO settings = getSettings();
        BigDecimal amount = settings.getWindowMaintenanceFee().multiply(BigDecimal.valueOf(quantity));
        String windowIds = windows.stream().map(window -> String.valueOf(window.getId())).collect(Collectors.joining(","));
        userBillServe.createBillAndUpdateBalance(
                userId,
                amount,
                BillType.PLATFORM,
                FundType.EXPENSE,
                TransactionType.GOLD_QUANT,
                "黄金量化窗口批量续费 ids=" + windowIds,
                "GQ_BATCH_" + IdWorker.getIdStr(),
                null,
                null,
                quantity,
                null
        );
        renewLockedWindows(windows, settings);
    }

    @Override
    public IPage<GoldQuantAccount> getAdminAccountPage(AdminGoldQuantAccountQueryDTO query) {
        if (query == null) {
            query = new AdminGoldQuantAccountQueryDTO();
        }
        LambdaQueryWrapper<GoldQuantAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getUserId() != null, GoldQuantAccount::getUserId, query.getUserId());
        wrapper.like(StringUtils.hasText(query.getWalletAddress()), GoldQuantAccount::getWalletAddress, query.getWalletAddress());
        if (StringUtils.hasText(query.getExpireStartTime())) {
            wrapper.ge(GoldQuantAccount::getHostingExpireTime, parseStart(query.getExpireStartTime()));
        }
        if (StringUtils.hasText(query.getExpireEndTime())) {
            wrapper.le(GoldQuantAccount::getHostingExpireTime, parseEnd(query.getExpireEndTime()));
        }
        if (StringUtils.hasText(query.getStartTime())) {
            wrapper.ge(GoldQuantAccount::getCreateTime, parseStart(query.getStartTime()));
        }
        if (StringUtils.hasText(query.getEndTime())) {
            wrapper.le(GoldQuantAccount::getCreateTime, parseEnd(query.getEndTime()));
        }
        wrapper.orderByAsc(GoldQuantAccount::getHostingExpireTime).orderByDesc(GoldQuantAccount::getId);
        return accountMapper.selectPage(new Page<>(safePage(query.getPage()), safeSize(query.getSize())), wrapper);
    }

    @Override
    public IPage<GoldQuantWindow> getAdminWindowPage(AdminGoldQuantWindowQueryDTO query) {
        if (query == null) {
            query = new AdminGoldQuantWindowQueryDTO();
        }
        LambdaQueryWrapper<GoldQuantWindow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getUserId() != null, GoldQuantWindow::getUserId, query.getUserId());
        wrapper.like(StringUtils.hasText(query.getWalletAddress()), GoldQuantWindow::getWalletAddress, query.getWalletAddress());
        wrapper.like(StringUtils.hasText(query.getWindowNo()), GoldQuantWindow::getWindowNo, query.getWindowNo());
        wrapper.eq(query.getStatus() != null, GoldQuantWindow::getStatus, query.getStatus());
        if (StringUtils.hasText(query.getExpireStartTime())) {
            wrapper.ge(GoldQuantWindow::getMaintenanceExpireTime, parseStart(query.getExpireStartTime()));
        }
        if (StringUtils.hasText(query.getExpireEndTime())) {
            wrapper.le(GoldQuantWindow::getMaintenanceExpireTime, parseEnd(query.getExpireEndTime()));
        }
        if (StringUtils.hasText(query.getStartTime())) {
            wrapper.ge(GoldQuantWindow::getCreateTime, parseStart(query.getStartTime()));
        }
        if (StringUtils.hasText(query.getEndTime())) {
            wrapper.le(GoldQuantWindow::getCreateTime, parseEnd(query.getEndTime()));
        }
        wrapper.orderByAsc(GoldQuantWindow::getMaintenanceExpireTime).orderByAsc(GoldQuantWindow::getId);
        return this.page(new Page<>(safePage(query.getPage()), safeSize(query.getSize())), wrapper);
    }

    private void renewLockedWindows(List<GoldQuantWindow> windows, GoldQuantSettingsVO settings) {
        LocalDateTime now = LocalDateTime.now();
        for (GoldQuantWindow window : windows) {
            LocalDateTime base = window.getMaintenanceExpireTime() == null || window.getMaintenanceExpireTime().isBefore(now)
                    ? now
                    : window.getMaintenanceExpireTime();
            this.update(new LambdaUpdateWrapper<GoldQuantWindow>()
                    .eq(GoldQuantWindow::getId, window.getId())
                    .set(GoldQuantWindow::getMaintenanceExpireTime, base.plusDays(settings.getMaintenanceDays())));
        }
    }

    private GoldQuantSettingsVO getSettings() {
        GoldQuantSettingsVO settings = systemConfigServe.getConfigObject(SETTINGS_KEY, GoldQuantSettingsVO.class);
        if (settings == null) {
            settings = new GoldQuantSettingsVO();
        }
        if (settings.getHostingFee() == null) {
            settings.setHostingFee(new BigDecimal("40"));
        }
        if (settings.getWindowMaintenanceFee() == null) {
            settings.setWindowMaintenanceFee(new BigDecimal("200"));
        }
        if (settings.getHostingDays() == null || settings.getHostingDays() <= 0) {
            settings.setHostingDays(30);
        }
        if (settings.getMaintenanceDays() == null || settings.getMaintenanceDays() <= 0) {
            settings.setMaintenanceDays(30);
        }
        if (settings.getMinerThreshold() == null || settings.getMinerThreshold() <= 0) {
            settings.setMinerThreshold(10);
        }
        if (settings.getMaxWindowCount() == null || settings.getMaxWindowCount() <= 0) {
            settings.setMaxWindowCount(10);
        }
        return settings;
    }

    private User getUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private GoldQuantAccount getAccount(Long userId) {
        return accountMapper.selectOne(new LambdaQueryWrapper<GoldQuantAccount>()
                .eq(GoldQuantAccount::getUserId, userId)
                .last("LIMIT 1"));
    }

    private GoldQuantAccount getAccountForUpdate(Long userId) {
        return accountMapper.selectOne(new LambdaQueryWrapper<GoldQuantAccount>()
                .eq(GoldQuantAccount::getUserId, userId)
                .last("LIMIT 1 FOR UPDATE"));
    }

    private int countUserMiners(Long userId) {
        return Math.toIntExact(userMinerMapper.selectCount(new LambdaQueryWrapper<UserMiner>()
                .eq(UserMiner::getUserId, userId)));
    }

    private int countUserWindows(Long userId) {
        return Math.toIntExact(this.count(new LambdaQueryWrapper<GoldQuantWindow>()
                .eq(GoldQuantWindow::getUserId, userId)
                .eq(GoldQuantWindow::getStatus, 1)));
    }

    private List<GoldQuantWindow> listUserWindows(Long userId) {
        return this.list(new LambdaQueryWrapper<GoldQuantWindow>()
                .eq(GoldQuantWindow::getUserId, userId)
                .eq(GoldQuantWindow::getStatus, 1)
                .orderByAsc(GoldQuantWindow::getMaintenanceExpireTime)
                .orderByAsc(GoldQuantWindow::getId));
    }

    private List<GoldQuantWindowVO> buildWindowVOList(List<GoldQuantWindow> windows) {
        List<GoldQuantWindowVO> result = new ArrayList<>();
        for (int i = 0; i < windows.size(); i++) {
            GoldQuantWindow window = windows.get(i);
            GoldQuantWindowVO vo = new GoldQuantWindowVO();
            vo.setId(window.getId());
            vo.setTitle("量化窗口" + (i + 1));
            vo.setWindowNo(window.getWindowNo());
            vo.setMaintenanceExpireTime(window.getMaintenanceExpireTime());
            result.add(vo);
        }
        return result;
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException("数量必须大于0");
        }
    }

    private LocalDateTime parseStart(String value) {
        return DateUtil.parse(value).toLocalDateTime().with(LocalTime.MIN);
    }

    private LocalDateTime parseEnd(String value) {
        return DateUtil.parse(value).toLocalDateTime().with(LocalTime.MAX);
    }

    private long safePage(Integer page) {
        return page == null || page <= 0 ? 1 : page;
    }

    private long safeSize(Integer size) {
        return size == null || size <= 0 ? 10 : size;
    }
}
