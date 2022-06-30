package com.github.donvip.archscrap.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NoticeTest {

	@Test
	void testIdPattern1() {
		Notice n = new Notice("1Fi1");
		assertEquals("1Fi1", n.getCote());
		assertEquals("1", n.getId());

		n = new Notice("11Fi 2800");
		assertEquals("11Fi 2800", n.getCote());
		assertEquals("2800", n.getId());
	}

	@Test
	void testIdPattern2() {
		Notice n = new Notice("VO4 70/455");
		assertEquals("VO4 70/455", n.getCote());
		assertEquals("70/455", n.getId());
	}
}
