package com.arellomobile.mvp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.arellomobile.mvp.presenter.PresenterField;
import com.arellomobile.mvp.presenter.PresenterType;

/**
 * Date: 18-Dec-15
 * Time: 13:16
 *
 * @author Alexander Blinov
 */
public class MvpProcessor
{
	private static final String TAG = "MvpProcessor";

	public static final String PRESENTER_BINDER_SUFFIX = "$$PresentersBinder";
	public static final String VIEW_STATE_SUFFIX = "$$State";
	public static final String FACTORY_PARAMS_HOLDER_SUFFIX = "$$ParamsHolder";
	public static final String PRESENTER_BINDER_INNER_SUFFIX = "Binder";
	public static final String VIEW_STATE_CLASS_NAME_PROVIDER_SUFFIX = "$$ViewStateClassNameProvider";

	/**
	 * Return all info about injected presenters and factories in view
	 *
	 * @param delegated   class contains presenter
	 * @param <Delegated> type of delegated
	 * @return PresenterBinder instance
	 */
	private <Delegated> PresenterBinder<? super Delegated> getPresenterBinder(Class<? super Delegated> delegated)
	{
		PresenterBinder<Delegated> binder;
		try
		{
			//noinspection unchecked
			binder = (PresenterBinder<Delegated>) findPresenterBinderForClass(delegated);
		}
		catch (InstantiationException e)
		{
			throw new IllegalStateException("can not instantiate binder for " + delegated.getName(), e);
		}
		catch (IllegalAccessException e)
		{
			throw new IllegalStateException("have no access to binder for " + delegated.getName(), e);
		}

		return binder;
	}

	/**
	 * Find generated binder for class
	 *
	 * @param clazz       class of presenter container
	 * @param <Delegated> type of presenter container
	 * @return PresenterBinder instance
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 */
	private <Delegated> PresenterBinder<? super Delegated> findPresenterBinderForClass(Class<Delegated> clazz)
			throws IllegalAccessException, InstantiationException
	{
		PresenterBinder<? super Delegated> presenterBinder;
		String clsName = clazz.getName();

		String className = clsName + PRESENTER_BINDER_SUFFIX;
		try
		{
			Class<?> presenterBinderClass = Class.forName(className);
			//noinspection unchecked
			presenterBinder = (PresenterBinder<? super Delegated>) presenterBinderClass.newInstance();

		}
		catch (ClassNotFoundException e)
		{
			return null;
		}
		//TODO add to binders array

		return presenterBinder;
	}

	/**
	 * 1) Generates tag for identification MvpPresenter using params.
	 * Custom presenter factory should use interface {@link ParamsProvider}'s method annotated with {@link ParamsProvider} to provide params from view
	 * <p>
	 * {@link com.arellomobile.mvp.DefaultPresenterFactory} works with {@link com.arellomobile.mvp.DefaultPresenterFactory.Params}.
	 * Default factory doesn't need in special method of view to provide params. It takes param from {@link com.arellomobile.mvp.presenter.InjectPresenter} annotation fields
	 * <p>
	 * 2) Checks if presenter with tag is already exist in {@link com.arellomobile.mvp.PresenterStore}, and returns it.
	 * <p>
	 * 3)If {@link com.arellomobile.mvp.PresenterStore} doesn't contain MvpPresenter with current tag, {@link com.arellomobile.mvp.PresenterFactory} will create it
	 *
	 * @param presenterField info about presenter from {@link com.arellomobile.mvp.presenter.InjectPresenter}
	 * @param delegated      class contains presenter
	 * @param delegateTag    unique tag generated by {@link MvpDelegate#generateTag()}
	 * @param <Delegated>    type of delegated
	 * @return MvpPresenter instance
	 */
	private <Delegated> MvpPresenter<? super Delegated> getMvpPresenter(PresenterField<? super Delegated> presenterField, Delegated delegated, String delegateTag)
	{
		Class<? extends MvpPresenter<?>> presenterClass = presenterField.getPresenterClass();
		Class<? extends PresenterFactory<?, ?>> presenterFactoryClass = presenterField.getFactory();
		ParamsHolder<?> holder = MvpFacade.getInstance().getPresenterFactoryStore().getParamsHolder(presenterField.getParamsHolderClass());
		PresenterStore presenterStore = MvpFacade.getInstance().getPresenterStore();
		PresenterFactory presenterFactory = MvpFacade.getInstance().getPresenterFactoryStore().getPresenterFactory(presenterFactoryClass);

		Object params = holder.getParams(presenterField, delegated, delegateTag);

		//TODO throw exception
		//noinspection unchecked
		String tag = presenterFactory.createTag(presenterClass, params);
		PresenterType type = presenterField.getPresenterType();

		//noinspection unchecked
		MvpPresenter<? super Delegated> presenter = presenterStore.get(type, tag, presenterClass);
		if (presenter != null)
		{
			return presenter;
		}

		//noinspection unchecked
		presenter = presenterFactory.createPresenter(presenterField.getDefaultInstance(), presenterClass, params);
		presenter.setPresenterType(type);
		presenter.setTag(tag);
		presenterStore.add(type, tag, presenter);

		return presenter;
	}


	/**
	 * Gets presenters {@link java.util.List} annotated with {@link com.arellomobile.mvp.presenter.InjectPresenter} for view.
	 * <p>
	 * See full info about getting presenter instance in {@link #getMvpPresenter}
	 *
	 * @param delegated   class contains presenter
	 * @param delegateTag unique tag generated by {@link MvpDelegate#generateTag()}
	 * @param <Delegated> type of delegated
	 * @return presenters list for specifies presenters container
	 */
	<Delegated> List<MvpPresenter<? super Delegated>> getMvpPresenters(Delegated delegated, String delegateTag)
	{
		@SuppressWarnings("unchecked")
		Class<? super Delegated> aClass = (Class<Delegated>) delegated.getClass();
		List<PresenterBinder<? super Delegated>> presenterBinders = new ArrayList<>();

		while (aClass != Object.class)
		{
			PresenterBinder<? super Delegated> presenterBinder = MvpFacade.getInstance().getMvpProcessor().getPresenterBinder(aClass);

			aClass = aClass.getSuperclass();

			if (presenterBinder == null)
			{
				continue;
			}

			presenterBinder.setTarget(delegated);
			presenterBinders.add(presenterBinder);
		}

		if (presenterBinders.isEmpty())
		{
			return Collections.emptyList();
		}

		List<MvpPresenter<? super Delegated>> presenters = new ArrayList<>();
		for (PresenterBinder<? super Delegated> presenterBinder : presenterBinders)
		{
			List<? extends PresenterField<? super Delegated>> presenterFields = presenterBinder.getPresenterFields();

			for (PresenterField<? super Delegated> presenterField : presenterFields)
			{
				MvpPresenter<? super Delegated> presenter = getMvpPresenter(presenterField, delegated, delegateTag);

				if (presenter != null)
				{
					presenters.add(presenter);
					presenterField.setValue(presenter);
				}
			}
		}

		return presenters;
	}
}
