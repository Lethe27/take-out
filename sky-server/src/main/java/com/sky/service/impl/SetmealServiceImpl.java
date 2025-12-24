package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private SetmealDishMapper setmealdishMapper;

    @Transactional
    public void addSetmeal(SetmealDTO setmealDTO){
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);

        setmealMapper.insert(setmeal);
        Long setmealId = setmeal.getId();

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();

        if(setmealDishes.size()>0){
            setmealDishes.forEach(setmealDish -> {
                setmealDish.setSetmealId(setmealId);
            });
        }
        setmealdishMapper.insertBatch(setmealDishes);
    }

    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> setmeals = setmealMapper.pageQuery(setmealPageQueryDTO);

        return new PageResult(setmeals.getTotal(), setmeals.getResult());
    }

    public SetmealVO getByIdWithSetmealDish(Long id) {
        return setmealMapper.getByIdWithSetmealDish(id);
    }

    @Transactional
    public void deteleWithSetmealDish(List<Long> ids) {

        //判断当前套餐是否能够删除---是否存在起售中的菜品？？
        for (Long id : ids) {
            SetmealVO setmealVO = setmealMapper.getByIdWithSetmealDish(id);
            if (setmealVO.getStatus() == 1) {
                //当前菜品处于起售中，不能删除
                throw new RuntimeException(setmealVO.getName() + "正在售卖中，不能删除");
            }
        }
        //删除套餐表中的数据
        setmealMapper.deleteBatch(ids);
    }

    @Transactional
    public void update(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);

        //更新套餐表中的基本信息
        setmealMapper.update(setmeal);

        Long setmealId = setmeal.getId();

        //删除套餐对应的菜品数据
        setmealdishMapper.deleteBySetmealId(setmealId);

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();

        if(setmealDishes.size()>0){
            setmealDishes.forEach(setmealDish -> {
                setmealDish.setSetmealId(setmealId);
            });
        }
        //重新插入套餐对应的菜品数据
        setmealdishMapper.insertBatch(setmealDishes);
    }

    public void updateStatus(Integer status, Long id) {
        Setmeal setmeal = Setmeal.builder()
                .status(status)
                .id(id)
                .build();
        setmealMapper.update(setmeal);
    }

    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }
}
