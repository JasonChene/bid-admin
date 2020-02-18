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

import cn.teleinfo.bidadmin.blockchain.entity.Company;
import cn.teleinfo.bidadmin.blockchain.vo.CompanyVO;
import cn.teleinfo.bidadmin.blockchain.mapper.CompanyMapper;
import cn.teleinfo.bidadmin.blockchain.service.ICompanyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springblade.core.mp.base.BaseServiceImpl;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 *  服务实现类
 *
 * @author Blade
 * @since 2019-10-16
 */
@Service
public class CompanyServiceImpl extends ServiceImpl<CompanyMapper, Company> implements ICompanyService {

	@Override
	public IPage<CompanyVO> selectCompanyPage(IPage<CompanyVO> page, CompanyVO company) {
		return page.setRecords(baseMapper.selectCompanyPage(page, company));
	}

}
