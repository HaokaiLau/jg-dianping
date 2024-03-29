package com.jgdp.controller;


import com.jgdp.dto.Result;
import com.jgdp.service.IShopTypeService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    /**
     * 查询店铺分类
     * @return
     */
    @GetMapping("list")
    public Result queryTypeList() {
        return typeService.queryList();
    }
}
