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
package cn.teleinfo.bidadmin.blockchain.service;

import cn.teleinfo.bidadmin.blockchain.entity.DposCanditionVoter;
import cn.teleinfo.bidadmin.blockchain.vo.DposCanditionVoterVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springblade.core.tool.api.R;

import java.util.List;

/**
 *  服务类
 *
 * @author Blade
 * @since 2019-10-17
 */
public interface IDposPullInfoService {

	R pullInfo();

	R pullCandidateListInfo(String address);

	R pullVoterListInfo(String address);

	R pullStakeListInfo(String address);

	R pullDocumentInfo(String address);

	R pullTrustAnchorInfo();

	R pullBaseTrustAnchorInfo();

	R pullExpendTrustAnchorInfo();

	R pullTrustAnchorVoterInfo();
}
