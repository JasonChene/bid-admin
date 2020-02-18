/**
 * Copyright (c) 2018-2028, Chill Zhuang 庄骞 (smallchill@163.com).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.teleinfo.bidadmin.blockchain.service.impl;

import cn.teleinfo.bidadmin.blockchain.entity.DposStake;
import cn.teleinfo.bidadmin.blockchain.vo.DposStakeVO;
import cn.teleinfo.bidadmin.blockchain.mapper.DposStakeMapper;
import cn.teleinfo.bidadmin.blockchain.service.IDposStakeService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springblade.core.mp.base.BaseServiceImpl;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 *  服务实现类
 *
 * @author Blade
 * @since 2019-10-17
 */
@Service
public class DposStakeServiceImpl extends ServiceImpl<DposStakeMapper, DposStake> implements IDposStakeService {

	@Override
	public IPage<DposStakeVO> selectDposStakePage(IPage<DposStakeVO> page, DposStakeVO dposStake) {
		return page.setRecords(baseMapper.selectDposStakePage(page, dposStake));
	}

	@Override
	public int saveBatchStakes(List<DposStake> stakes) {
		return baseMapper.saveBatchStakes(stakes);
	}

	@Override
	public void saveOrUpdateStake(DposStake stake) {
		QueryWrapper<DposStake> voteQueryWrapper = new QueryWrapper<>();
		voteQueryWrapper.eq("Owner", stake.getOwner());
		voteQueryWrapper.last("limit 1");

		DposStake trustanchorVote = baseMapper.selectOne(voteQueryWrapper);

		if (trustanchorVote == null) {
			baseMapper.insert(stake);
		} else {
			stake.setId(trustanchorVote.getId());
			baseMapper.updateById(stake);
		}
	}

}
