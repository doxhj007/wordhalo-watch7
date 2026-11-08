package cn.wordhalo.watch;

import java.util.*;

/** Concrete books. Study always has one active book; books are never merged in the UI. */
public final class BookCatalog {
    public static final long CET4_STAR=1L, CET4_NEW_ORIENTAL=1L<<1, CET4_FLASH=1L<<2;
    public static final long CET6_STAR=1L<<3, CET6_NEW_ORIENTAL=1L<<4, CET6_FLASH=1L<<5;
    public static final long ALL_MASK=(1L<<6)-1, CET4_MASK=7L, CET6_MASK=56L;

    public static final class Book {
        public final long bit;
        public final int level, headwords;
        public final String brand, title, edition;
        Book(long bit,int level,String brand,String title,String edition,int headwords){
            this.bit=bit;this.level=level;this.brand=brand;this.title=title;this.edition=edition;this.headwords=headwords;
        }
        public String label(){return brand+" · "+title;}
        public String shortLabel(){return brand+" · "+headwords+"词";}
    }

    public static final List<Book> ALL=Collections.unmodifiableList(Arrays.asList(
        new Book(CET4_STAR,4,"星火","四级词汇必背乱序版","2014年3月第1版",2261),
        new Book(CET4_NEW_ORIENTAL,4,"新东方","四级词汇正序版","2013年11月第1版",4447),
        new Book(CET4_FLASH,4,"闪过","四级词汇闪过","公开整理词序",5821),
        new Book(CET6_STAR,6,"星火","六级巧记速记乱序版","2009年7月第1版",2999),
        new Book(CET6_NEW_ORIENTAL,6,"新东方","六级红宝书正序版","2013年8月第1版",2744),
        new Book(CET6_FLASH,6,"闪过","六级词汇乱序版","公开整理词序",5325)
    ));

    public static long defaultBook(){return CET6_STAR;}
    public static long defaultMask(){return defaultBook();}
    public static long sanitize(long bit){return find(bit)==null?defaultBook():bit;}
    public static Book find(long bit){for(Book book:ALL)if(book.bit==bit)return book;return null;}
    public static List<Book> forLevel(int level){ArrayList<Book> out=new ArrayList<>();for(Book b:ALL)if(b.level==level)out.add(b);return out;}
    private BookCatalog(){}
}
