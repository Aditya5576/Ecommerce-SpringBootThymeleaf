package com.s13sh.ecommerce.service.implementation;

import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;

import com.s13sh.ecommerce.dto.Customer;
import com.s13sh.ecommerce.dto.Item;
import com.s13sh.ecommerce.dto.Product;
import com.s13sh.ecommerce.helper.AES;
import com.s13sh.ecommerce.helper.MyEmailSender;
import com.s13sh.ecommerce.repository.CustomerRepository;
import com.s13sh.ecommerce.repository.ProductRepository;
import com.s13sh.ecommerce.repository.SellerRepository;
import com.s13sh.ecommerce.service.CustomerService;
import com.s13sh.ecommerce.service.ItemRepository;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@Service
public class CustomerServiceImpl implements CustomerService {

	@Autowired
	Customer customer;

	@Autowired
	MyEmailSender emailSender;

	@Autowired
	ItemRepository itemRepository;

	@Autowired
	CustomerRepository customerRepository;

	@Autowired
	SellerRepository sellerRepository;

	@Autowired
	ProductRepository productRepository;

	@Override
	public String loadRegister(ModelMap map) {
		map.put("customer", customer);
		return "customer-register.html";
	}

	@Override
	public String loadRegister(@Valid Customer customer, BindingResult result, HttpSession session) {
		if (!customer.getPassword().equals(customer.getConfirmpassword()))
			result.rejectValue("confirmpassword", "error.confirmpassword", "* Password Missmatch");
		if (customerRepository.existsByEmail(customer.getEmail())
				|| sellerRepository.existsByEmail(customer.getEmail()))
			result.rejectValue("email", "error.email", "* Email should be Unique");
		if (customerRepository.existsByMobile(customer.getMobile())
				|| sellerRepository.existsByMobile(customer.getMobile()))
			result.rejectValue("mobile", "error.mobile", "* Mobile Number should be Unique");

		if (result.hasErrors())
			return "customer-register.html";
		else {
			int otp = new Random().nextInt(100000, 1000000);
			customer.setOtp(otp);
			customer.setPassword(AES.encrypt(customer.getPassword(), "123"));
			customerRepository.save(customer);
			emailSender.sendOtp(customer);

			session.setAttribute("success", "Otp Sent Success");
			session.setAttribute("id", customer.getId());
			return "redirect:/customer/otp";
		}
	}

	@Override
	public String submitOtp(int id, int otp, HttpSession session) {
		Customer customer = customerRepository.findById(id).orElseThrow();
		if (customer.getOtp() == otp) {
			customer.setVerified(true);
			customerRepository.save(customer);
			session.setAttribute("success", "Account Created Success");
			return "redirect:/";
		} else {
			session.setAttribute("failure", "Invalid OTP");
			session.setAttribute("id", customer.getId());
			return "redirect:/customer/otp";
		}
	}

	@Override
	public String viewProducts(HttpSession session, ModelMap map) {
		if (session.getAttribute("customer") != null) {
			List<Product> products = productRepository.findByApprovedTrue();
			if (products.isEmpty()) {
				session.setAttribute("failure", "No Products Found");
				return "redirect:/customer/home";
			} else {
				Customer customer=(Customer) session.getAttribute("customer");
				map.put("items",customer.getCart().getItems());
				map.put("products", products);
				return "customer-products.html";
			}
		} else {
			session.setAttribute("failure", "Invalid Session, Login Again");
			return "redirect:/login";
		}
	}

	@Override
	public String loadHome(HttpSession session) {
		if (session.getAttribute("customer") != null)
			return "customer-home.html";
		else {
			session.setAttribute("failure", "Invalid Session, Login Again");
			return "redirect:/login";
		}
	}

	@Override
	public String addToCart(HttpSession session, int id) {
		if (session.getAttribute("customer") != null) {
			Product product = productRepository.findById(id).orElseThrow();
			if (product.getStock() < 1) {
				session.setAttribute("failure", "Out of Stock");
				return "redirect:/customer/products";
			} else {
				product.setStock(product.getStock() - 1);
				productRepository.save(product);

				Customer customer = (Customer) session.getAttribute("customer");

				List<Item> items = customer.getCart().getItems();
				if (items.isEmpty()) {
					Item item = new Item();
					item.setCategory(product.getCategory());
					item.setName(product.getName());
					item.setDescription(product.getDescription());
					item.setImageLink(product.getImageLink());
					item.setPrice(product.getPrice());
					item.setQuantity(1);
					items.add(item);

					customer.getCart().setItems(items);

					session.setAttribute("success", "Added to Cart Success");
				} else {
					boolean flag = true;

					for (Item item : items) {
						if (item.getName().equalsIgnoreCase(product.getName())) {
							item.setPrice(item.getPrice() + product.getPrice());
							item.setQuantity(item.getQuantity() + 1);
							flag = false;
						}
					}

					if (flag) {
						Item item = new Item();
						item.setCategory(product.getCategory());
						item.setName(product.getName());
						item.setDescription(product.getDescription());
						item.setImageLink(product.getImageLink());
						item.setPrice(product.getPrice());
						item.setQuantity(1);
						items.add(item);

					}
					customer.getCart().setItems(items);

					session.setAttribute("success", "Added to Cart Success");
				}

				customer.getCart()
						.setPrice(customer.getCart().getItems().stream().mapToDouble(x -> x.getPrice()).sum());
				customerRepository.save(customer);

				session.setAttribute("customer", customerRepository.findById(customer.getId()).orElseThrow());
				return "redirect:/customer/products";
			}
		} else {
			session.setAttribute("failure", "Invalid Session, Login Again");
			return "redirect:/login";
		}
	}

	@Override
	public String resendOtp(int id, HttpSession session) {
		Customer customer = customerRepository.findById(id).orElseThrow();
		int otp = new Random().nextInt(100000, 1000000);
		customer.setOtp(otp);
		customerRepository.save(customer);
		emailSender.sendOtp(customer);

		session.setAttribute("success", "Otp Resent Success");
		session.setAttribute("id", customer.getId());
		return "redirect:/customer/otp";
	}

	@Override
	public String removeFromCart(HttpSession session, int id) {
		if (session.getAttribute("customer") != null) {
			Product product = productRepository.findById(id).orElseThrow();
			Customer customer = (Customer) session.getAttribute("customer");
			List<Item> items = customer.getCart().getItems();
			if (items.isEmpty()) {
				session.setAttribute("failure", "No Item in Cart");
			} else {
				Item item2 = null;
				for (Item item : items) {
					if (item.getName().equals(product.getName())) {
						item2 = item;
						break;
					}
				}
				if (item2 == null) {
					session.setAttribute("failure", "No Item in Cart");
				} else {
					product.setStock(product.getStock() + 1);
					productRepository.save(product);
					session.setAttribute("success", "Item Removed Success");
					if (item2.getQuantity() > 1) {
						item2.setQuantity(item2.getQuantity() - 1);
						item2.setPrice(item2.getPrice() - product.getPrice());
						itemRepository.save(item2);
					} else {
						customer.getCart().getItems().remove(item2);
						customerRepository.save(customer);
						itemRepository.delete(item2);
					}
				}
			}
			customer.getCart().setPrice(customer.getCart().getItems().stream().mapToDouble(x -> x.getPrice()).sum());

			session.setAttribute("customer", customerRepository.findById(customer.getId()).orElseThrow());
			return "redirect:/customer/products";
		} else {
			session.setAttribute("failure", "Invalid Session, Login Again");
			return "redirect:/login";
		}
	}

	@Override
	public String viewCart(HttpSession session, ModelMap map) {
		if (session.getAttribute("customer") != null) {
			Customer customer = (Customer) session.getAttribute("customer");
			if(customer.getCart().getItems().isEmpty()) {
				session.setAttribute("failure", "No Item in Cart");
				return "redirect:/customer/home";
			}else{
			map.put("cart", customer.getCart());
			return "customer-cart.html";
			}
		} else {
			session.setAttribute("failure", "Invalid Session, Login Again");
			return "redirect:/login";
		}
	}

	@Override
	public String addToCartItem(HttpSession session, int id) {
		if (session.getAttribute("customer") != null) {
			Item item=itemRepository.findById(id).orElseThrow();
			Product product=productRepository.findByName(item.getName()).get(0);
			return addToCart(session, product.getId());
		}
		else{
			session.setAttribute("failure", "Invalid Session, Login Again");
            return "redirect:/login";
		}
	}

	@Override
	public String removeFromCartItem(HttpSession session, int id) {
		if (session.getAttribute("customer") != null) {
			Item item=itemRepository.findById(id).orElseThrow();
			Product product=productRepository.findByName(item.getName()).get(0);
			return removeFromCartItem(session, product.getId());
		}
		else{
			session.setAttribute("failure", "Invalid Session, Login Again");
            return "redirect:/login";
		}
	}

}
