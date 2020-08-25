package io.vertx.common;

public class Pagination {

	private int listSize = 10; // 한 페이지당 보여질 리스트 개수
	private int rangeSize = 10; // 한 페이지 범위에 보여질 페이지의 개수
	private int page; // 현재 목록의 페이지 번호
	private int range; // 각 페이지 범위 시작 번호
	private int listCnt; // 전채 게시물의 개수
	private int pageCnt; // 전체 페이지 범위의 개수
	private int startPage; // 각 페이지 범위 시작 번호
	private int startList; // 해당 페이지 당 게시판 시작 번호
	private int endPage; // 각 페이지 범위 끝 번호
	private boolean prev; // 이전 페이지 버튼 여부
	private boolean next; // 다음 페이지 버튼 여부

	public void pageInfo(int page, int range, int listCnt) {
		this.page = page;
		this.range = range;
		this.listCnt = listCnt;

		// 전체 페이지수
		this.pageCnt = (int) Math.ceil((float)listCnt / (float)listSize);
	
		// 시작 페이지
		this.startPage = (range - 1) * rangeSize + 1;

		// 끝 페이지
		this.endPage = range * rangeSize;

		// 게시판 시작번호
		this.startList = (page - 1) * listSize;

		// 이전 버튼 상태
		this.prev = range == 1 ? false : true;


		// 다음 버튼 상태
//		this.next = endPage > pageCnt ? false : true;
		if (this.endPage > this.pageCnt) {
			
			System.err.println(this.listCnt % 1);
			
			if(this.listCnt % this.pageCnt == 0 ) {
				
				this.endPage = this.pageCnt;

			} else if(this.pageCnt * this.listSize > this.listCnt) {
				
				this.endPage = this.pageCnt;
			} else {
				
				this.endPage = this.pageCnt + 1;
				this.next = false;
				
			}
			System.out.println("??????????" + this.listCnt % this.pageCnt);
//			if(this.listCnt % this.pageCnt == 0) {
//				this.endPage = this.pageCnt;
//			}
		}
		
		
		
		//int test = (this.listCnt / this.pageCnt) - pageCnt;
		System.out.println("listCntlistCntlistCntlistCntlistCntlistCnt" + this.listCnt);
		System.out.println("pageCntpageCntpageCntpageCntpageCntpageCnt" + this.pageCnt);
//		if((this.listCnt / this.pageCnt) - pageCnt == 0) {
//			this.endPage = this.pageCnt;
//		}
	}

	public int getListSize() {
		return listSize;
	}

	public void setListSize(int listSize) {
		this.listSize = listSize;
	}

	public int getRangeSize() {
		return rangeSize;
	}

	public void setRangeSize(int rangeSize) {
		this.rangeSize = rangeSize;
	}

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = page;
	}

	public int getRange() {
		return range;
	}

	public void setRange(int range) {
		this.range = range;
	}

	public int getListCnt() {
		return listCnt;
	}

	public void setListCnt(int listCnt) {
		this.listCnt = listCnt;
	}

	public int getPageCnt() {
		return pageCnt;
	}

	public void setPageCnt(int pageCnt) {
		this.pageCnt = pageCnt;
	}

	public int getStartPage() {
		return startPage;
	}

	public void setStartPage(int startPage) {
		this.startPage = startPage;
	}

	public int getStartList() {
		return startList;
	}

	public void setStartList(int startList) {
		this.startList = startList;
	}

	public int getEndPage() {
		return endPage;
	}

	public void setEndPage(int endPage) {
		this.endPage = endPage;
	}

	public boolean isPrev() {
		return prev;
	}

	public void setPrev(boolean prev) {
		this.prev = prev;
	}

	public boolean isNext() {
		return next;
	}

	public void setNext(boolean next) {
		this.next = next;
	}

	@Override
	public String toString() {
		return "{\"listSize\":\"" + listSize + "\", \"rangeSize\":\"" + rangeSize + "\", \"page\":\"" + page + "\", \"range\":\"" + range
				+ "\", \"listCnt\":\"" + listCnt + "\", \"pageCnt\":\"" + pageCnt + "\", \"startPage\":\"" + startPage + "\", \"startList\":\""
				+ startList + "\", \"endPage\":\"" + endPage + "\", \"prev\":\"" + prev + "\", \"next\":\"" + next + "\"}";
	}
	
	

}
