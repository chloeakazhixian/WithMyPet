package pet.mvc.controller;

import java.util.Date;
import java.util.Hashtable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j;
import pet.mvc.service.WalkService;
import pet.mvc.walk.CmtVo;
import pet.mvc.walk.Comment;
import pet.mvc.walk.Mailer;
import pet.mvc.walk.Walk;
import pet.mvc.walk.WalkListResult;
import pet.mvc.walk.joinVo;

@Log4j
@Controller
@RequestMapping("walk")

public class WalkController {
	@Autowired
	private WalkService walkService;
	@Autowired(required=false)
	private Mailer mailer;
	
	// 리스트 불러오기
	@RequestMapping("list.do")
	public ModelAndView list(HttpSession session, 
			@RequestParam (defaultValue="0", required=false)int cp,
			@RequestParam (defaultValue="0",required=false)int ps,
			String searchType,String keyword) {
		log.info("##파라미터 값 cp: "+cp+", searchType: "+searchType+", keyword: "+keyword);
		
		// 페이징
		if(cp == 0) {
			Object cpObj = session.getAttribute("cp");
			if(cpObj != null) cp = (Integer)cpObj;
		}
		session.setAttribute("cp", cp);
		if(ps == 0) {
			Object psObj = session.getAttribute("ps");
			if(psObj != null) ps=(Integer)psObj;
		}else {
			Object psObj = session.getAttribute("ps");
			if(psObj != null) {
				int psSession = (Integer)psObj;
				if(psSession != ps) {
					cp = 1;
					session.setAttribute("cp", cp);
				}
			}
		}
		session.setAttribute("ps", ps);
		// 정렬방식
		if(searchType == null) {
			String orderSession = (String) session.getAttribute("orderType");
			if(orderSession != null) orderSession = orderSession.trim();
			searchType = orderSession;
		}
		session.setAttribute("searchType", searchType);
		// 검색키워드
		if(keyword == null) {
			String keywordSession = (String) session.getAttribute("keyword");
			if(keywordSession != null) keywordSession = keywordSession.trim();
			keyword = keywordSession;
		}
		session.setAttribute("keyword", keyword);
		// DB에서 입력정보로 리스트 뽑아내기
		log.info("##서비스 넘어간 값 cp: "+cp+", searchType: "+searchType+", keyword: "+keyword);
		WalkListResult list = walkService.getListS(cp,ps,searchType,keyword);
		log.info("##검색한 리스트:"+list);
		
		ModelAndView mv = new ModelAndView("pet/walklist","list",list);
		// 리스트가 비어있을 경우
		if(list.getList().size() == 0) {
			if(cp>1) return new ModelAndView("redirect:list.do?cp="+(cp-1));
			else return new ModelAndView("pet/walklist","list",null);
		}else {
			return mv;
		}
	}
	
	// 모임 개설
	@RequestMapping(value="make.do")
	public String make(Walk dto)  {
		String from = (dto.getTime())+":00.000";
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS");
		walkService.insertWalk(dto);
		return "redirect:list.do";
	}
	
	
	@GetMapping(value="apply.do", produces = {MediaType.APPLICATION_JSON_UTF8_VALUE, MediaType.APPLICATION_XML_VALUE})
	public @ResponseBody CmtVo apply(String walk_cmt_writer, String walk_cmt_content, Long walk_idx) {
		Comment dto = new Comment();
		dto.setWalk_idx(walk_idx);
		dto.setWalk_cmt_writer(walk_cmt_writer);
		dto.setWalk_cmt_content(walk_cmt_content);
		boolean flag = walkService.insertWalkCmt(dto);
		log.info("여기용"+flag);
		log.info("dto"+dto.getMember_number());
		if(flag) {
			try {
				String receiver = "misty901@naver.com";  //임시
				String subject = dto.getWalk_cmt_writer()+"님이 함께 산책하고 싶어합니다!";
				String content = dto.getWalk_cmt_writer()+"님이 보낸 메세지입니다. : "+dto.getWalk_cmt_content()
				+" /  자세한 내용 보기 : http://localhost:8080/walk/blog.do?idx="+dto.getWalk_idx();
				mailer.sendMail(receiver,subject,content);
				CmtVo allCmts = walkService.getWalkCmt(walk_idx);
				return allCmts;
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}else {
			return null;
		}
	}
	
	
	
	@GetMapping("update.do")
	public ModelAndView update(long idx) {
		Walk dto = walkService.getWalk(idx);
		log.info(dto);
		ModelAndView mv = new ModelAndView("pet/walkUpdate","content",dto);
		return mv;
	}
	
	@PostMapping("update.do")
	public String rewrite(Walk dto) {
		walkService.walkUpdate(dto);
		return "redirect:list.do";
	}
	
	@GetMapping("delete.do")
	public String delete(long idx) {
		walkService.walkDelete(idx);
		return "redirect:list.do?cp=1";
	}
	
	@GetMapping(value="getMemberData.do", produces = {MediaType.APPLICATION_JSON_UTF8_VALUE, MediaType.APPLICATION_XML_VALUE})
	public @ResponseBody Comment getCmtMember(long idx, HttpServletResponse response) {
		//임의로, 멤버 연동되면 멤버 정보 가져와야함.
		Comment dto = walkService.getWalkCmtData(idx);
		return dto;
	}
	
	@RequestMapping("blog.do")
	public ModelAndView walkblog(HttpSession session, HttpServletRequest request, long idx) {
		Walk dto = walkService.getWalk(idx);
		Date origin = dto.getWalk_date();
		DateFormat dayForm = new SimpleDateFormat("yyyy년 MM월 dd일");
		DateFormat timeForm = new SimpleDateFormat("a hh시 mm분");
		String day = dayForm.format(origin);
		String time = timeForm.format(origin);
		Hashtable<String,Object> map = new Hashtable<String,Object>();
		map.put("day",day);
		map.put("time",time);
		map.put("dto",dto);
		ModelAndView mv = new ModelAndView("pet/walkblog","content",map);
		return mv;
	}
	
	@GetMapping(value="like.do", produces = {MediaType.APPLICATION_JSON_UTF8_VALUE, MediaType.APPLICATION_XML_VALUE})
	public @ResponseBody int like(Long walk_idx,HttpServletResponse response) {
		joinVo vo = new joinVo(walk_idx,1L);
		walkService.addHeart(vo);
		int likeCount = walkService.getWalkLike(walk_idx);
		return likeCount;
	}
	
	@GetMapping(value="join.do", produces = {MediaType.APPLICATION_JSON_UTF8_VALUE, MediaType.APPLICATION_XML_VALUE})
	public @ResponseBody CmtVo join(Long joinIdx, Long joinWalkIdx, HttpServletResponse response) {
		log.info(joinIdx+"이거랑, "+joinWalkIdx);
		long memberNo = walkService.selectByCmtIdx(joinIdx); // cmtIdx로 memNo 뽑기
		joinVo vo = new joinVo(joinWalkIdx,memberNo);
		boolean flag = walkService.insertWalkJoin(vo,joinIdx);
		if(flag) {
			CmtVo allCmts = walkService.getWalkCmt(joinWalkIdx);
			return allCmts; 
		}else return null;
	}
	
	@RequestMapping("post.do")
	public String walkpost() {
		return "/pet/walkpost";
	}
	
	@GetMapping(value="search.do", produces = {MediaType.APPLICATION_JSON_UTF8_VALUE, MediaType.APPLICATION_XML_VALUE})
	public @ResponseBody WalkListResult search(String keyword, String searchType) {
		WalkListResult list = walkService.getListS(1,10,searchType,keyword);
		return list;
	}
}
